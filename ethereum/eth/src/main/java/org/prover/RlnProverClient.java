/*
 * Copyright contributors to Besu.
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
package org.prover;

import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.vac.prover.RlnProverGrpc;
import net.vac.prover.SendTransactionReply;
import net.vac.prover.SendTransactionRequest;

public class RlnProverClient {
  private static final Logger logger = Logger.getLogger(RlnProverClient.class.getName());

  private final RlnProverGrpc.RlnProverFutureStub futureStub;

  /** Construct client connecting to gRPC server at {@code host:port}. */
  public RlnProverClient(final String host, final int port) {
    this(
        ManagedChannelBuilder.forAddress(host, port)
            // Channels are secure by default (via SSL/TLS). For the example we disable TLS
            // to avoid
            // needing certificates.
            .usePlaintext()
            .build());
  }

  /**
   * Construct client for given {@link ManagedChannel}.
   *
   * @param channel the channel to use to make calls.
   */
  RlnProverClient(final ManagedChannel channel) {
    futureStub = RlnProverGrpc.newFutureStub(channel);
  }

  /** Send transaction to server asynchronously. */
  public void sendTransaction(final Transaction transaction) {
    logger.info("Will try to send transaction asynchronously...");
    SendTransactionRequest request = TransactionMapper.toRequest(transaction);
    ListenableFuture<SendTransactionReply> future = futureStub.sendTransaction(request);

    Futures.addCallback(
        future,
        new FutureCallback<SendTransactionReply>() {
          @Override
          public void onSuccess(final SendTransactionReply reply) {
            logger.info("Asynchronous transaction sent successfully: " + reply);
          }

          @Override
          public void onFailure(final Throwable t) {
            logger.log(Level.WARNING, "RPC failed asynchronously: {0}", t);
          }
        },
        MoreExecutors.directExecutor()); // Executes the callbacks in the calling thread
  }
}
