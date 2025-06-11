/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.transactions.grpc;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.vac.prover.Address;
import net.vac.prover.RlnProverGrpc;
import net.vac.prover.SendTransactionReply;
import net.vac.prover.SendTransactionRequest;
import net.vac.prover.U256;
import net.vac.prover.Wei;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC-based transaction pool validator that sends local transactions to an external RLN prover
 * service for validation.
 *
 * <p>This validator demonstrates: - Only validates local transactions (isLocal=true) - Falls back
 * to default validation for peer transactions - Gracefully handles gRPC failures by falling back to
 * default validation - Logs all transaction validation attempts for verification
 */
public class GrpcTransactionPoolValidator implements PluginTransactionPoolValidator {
  private static final Logger LOG = LoggerFactory.getLogger(GrpcTransactionPoolValidator.class);

  private final String endpoint;
  private int validationCallCount = 0;
  private int localTransactionCount = 0;
  private int peerTransactionCount = 0;

  private final ManagedChannel channel;
  private final RlnProverGrpc.RlnProverBlockingStub blockingStub;

  /**
   * Creates a new gRPC transaction pool validator.
   *
   * @param endpoint the gRPC service endpoint (host:port)
   */
  public GrpcTransactionPoolValidator(final String endpoint) {
    this.endpoint = endpoint;
    this.channel = createChannel(endpoint);
    this.blockingStub = RlnProverGrpc.newBlockingStub(channel);
    LOG.info("*** gRPC Transaction Pool Validator INITIALIZED for endpoint: {} ***", endpoint);
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  /** Creates a gRPC channel. Protected to allow overriding in tests. */
  protected ManagedChannel createChannel(final String endpoint) {
    return ManagedChannelBuilder.forTarget(endpoint).usePlaintext().build();
  }

  @Override
  public Optional<String> validateTransaction(
      final Transaction transaction, final boolean isLocal, final boolean hasPriority) {

    validationCallCount++;

    LOG.info("*** TRANSACTION VALIDATION #{} ***", validationCallCount);
    LOG.info("Transaction Hash: {}", transaction.getHash().toHexString());
    LOG.info("Transaction Sender: {}", transaction.getSender().toHexString());
    LOG.info(
        "Transaction Gas Price: {}", transaction.getGasPrice().map(Object::toString).orElse("N/A"));
    LOG.info("Transaction Value: {}", transaction.getValue().toHexString());
    LOG.info("Is Local: {}", isLocal);
    LOG.info("Has Priority: {}", hasPriority);
    LOG.info("gRPC Endpoint: {}", endpoint);

    // Only validate local transactions via gRPC
    if (!isLocal) {
      peerTransactionCount++;
      LOG.debug("Skipping gRPC validation for peer transaction");
      return Optional.empty(); // Accept peer transactions without gRPC validation
    }

    localTransactionCount++;
    LOG.debug("Processing local transaction via gRPC: {}", transaction.getHash());

    try {
      SendTransactionRequest.Builder requestBuilder = SendTransactionRequest.newBuilder();

      transaction
          .getGasPrice()
          .ifPresent(
              gasPrice ->
                  requestBuilder.setGasPrice(
                      Wei.newBuilder()
                          .setValue(ByteString.copyFrom(gasPrice.getAsBigInteger().toByteArray()))
                          .build()));

      requestBuilder.setSender(
          Address.newBuilder()
              .setValue(ByteString.copyFrom(transaction.getSender().toArrayUnsafe()))
              .build());

      transaction
          .getChainId()
          .ifPresent(
              chainId ->
                  requestBuilder.setChainId(
                      U256.newBuilder()
                          .setValue(ByteString.copyFrom(chainId.toByteArray()))
                          .build()));

      requestBuilder.setTransactionHash(ByteString.copyFrom(transaction.getHash().toArrayUnsafe()));

      SendTransactionRequest request = requestBuilder.build();

      LOG.debug("Sending transaction to RLN prover: {}", request);
      SendTransactionReply reply = blockingStub.sendTransaction(request);

      if (reply.getResult()) {
        LOG.debug("RLN prover accepted transaction {}", transaction.getHash());
        return Optional.empty(); // Transaction is valid
      } else {
        LOG.warn("RLN prover rejected transaction {}", transaction.getHash());
        return Optional.of("RLN prover rejected transaction");
      }

    } catch (final Exception e) {
      LOG.warn(
          "gRPC validation failed for transaction {}, falling back to default validation: {}",
          transaction.getHash(),
          e.getMessage());
      // Graceful fallback: accept the transaction if gRPC fails
      return Optional.empty();
    }
  }

  /** Close the gRPC channel. */
  public void close() {
    try {
      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while shutting down gRPC channel", e);
      Thread.currentThread().interrupt();
    }
  }

  /** Get statistics for testing/verification. */
  public int getValidationCallCount() {
    return validationCallCount;
  }

  public int getLocalTransactionCount() {
    return localTransactionCount;
  }

  public int getPeerTransactionCount() {
    return peerTransactionCount;
  }

  public String getEndpoint() {
    return endpoint;
  }
}
