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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.vac.prover.RlnProverGrpc;
import net.vac.prover.SendTransactionReply;
import net.vac.prover.SendTransactionRequest;

public class RlnProverClient {
  private static final Logger logger = Logger.getLogger(RlnProverClient.class.getName());

  private final RlnProverGrpc.RlnProverFutureStub futureStub;

  public RlnProverClient(final String host, final int port) {
    this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
  }

  RlnProverClient(final ManagedChannel channel) {
    futureStub = RlnProverGrpc.newFutureStub(channel);
  }

  public void sendTransaction(final Transaction transaction) {
    logger.log(Level.INFO, "Sending transaction to RLN Prover service: {0}", transaction);

    SendTransactionRequest request = TransactionMapper.toRequest(transaction);
    ListenableFuture<SendTransactionReply> future = futureStub.sendTransaction(request);

    Futures.addCallback(
        future,
        new FutureCallback<SendTransactionReply>() {
          @Override
          public void onSuccess(final SendTransactionReply reply) {
            logger.log(Level.INFO, "Successfully sent transaction. Received reply: {0}", reply);
          }

          @Override
          public void onFailure(final Throwable t) {
            if (t instanceof StatusRuntimeException) {
              StatusRuntimeException sre = (StatusRuntimeException) t;
              Status.Code code = sre.getStatus().getCode();

              if (code == Status.Code.NOT_FOUND) {
                logger.log(
                    Level.WARNING,
                    "Transaction failed: Sender not found (NOT_FOUND). This may indicate the sender is not registered.");
                return;
              }

              logger.log(
                  Level.SEVERE,
                  "gRPC error occurred while sending transaction. Status: {0}, Description: {1}",
                  new Object[] {code, sre.getStatus().getDescription()});
            } else {
              logger.log(
                  Level.SEVERE,
                  "Unexpected error occurred while sending transaction to RLN Prover service",
                  t);
            }
          }
        },
        MoreExecutors.directExecutor());
  }
}
