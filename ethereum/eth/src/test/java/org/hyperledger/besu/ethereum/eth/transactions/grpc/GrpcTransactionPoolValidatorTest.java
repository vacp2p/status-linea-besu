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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for GrpcTransactionPoolValidator that focus on the actual implementation logic: -
 * Transaction processing behavior (local vs peer) - Error handling and fallback mechanisms -
 * Statistics tracking - Transaction data extraction and formatting
 */
class GrpcTransactionPoolValidatorTest {

  private static final SignatureAlgorithm SIGNATURE_ALGORITHM =
      SignatureAlgorithmFactory.getInstance();

  private GrpcTransactionPoolValidator validator;

  @AfterEach
  void tearDown() {
    if (validator != null) {
      validator.close();
    }
  }

  @Test
  void shouldSkipPeerTransactionsCompletely() {
    // Given: validator with invalid endpoint to ensure no real gRPC calls
    validator = new GrpcTransactionPoolValidator("invalid-endpoint:9999");
    Transaction peerTransaction = createTestTransaction();

    // When: processing peer transaction
    Optional<String> result = validator.validateTransaction(peerTransaction, false, false);

    // Then: should accept without any gRPC processing
    assertThat(result).isEmpty(); // Accepted
    assertThat(validator.getPeerTransactionCount()).isEqualTo(1);
    assertThat(validator.getLocalTransactionCount()).isEqualTo(0);
    assertThat(validator.getValidationCallCount()).isEqualTo(1);
  }

  @Test
  void shouldProcessLocalTransactionsViaGrpc() {
    // Given: validator with invalid endpoint to test fallback behavior
    validator = new GrpcTransactionPoolValidator("invalid-endpoint:9999");
    Transaction localTransaction = createTestTransaction();

    // When: processing local transaction
    Optional<String> result = validator.validateTransaction(localTransaction, true, false);

    // Then: should attempt gRPC call and fallback to acceptance
    assertThat(result).isEmpty(); // Accepted via fallback
    assertThat(validator.getLocalTransactionCount()).isEqualTo(1);
    assertThat(validator.getPeerTransactionCount()).isEqualTo(0);
    assertThat(validator.getValidationCallCount()).isEqualTo(1);
  }

  @Test
  void shouldFallBackGracefullyWhenGrpcServiceUnavailable() {
    // Given: validator pointing to unavailable service
    validator = new GrpcTransactionPoolValidator("localhost:99999");
    Transaction localTransaction = createTestTransaction();

    // When: attempting to validate local transaction
    Optional<String> result = validator.validateTransaction(localTransaction, true, false);

    // Then: should fall back to accepting the transaction
    assertThat(result).isEmpty(); // Graceful fallback accepts transaction
    assertThat(validator.getLocalTransactionCount()).isEqualTo(1);
  }

  @Test
  void shouldTrackStatisticsAccurately() {
    // Given: validator for testing statistics
    validator = new GrpcTransactionPoolValidator("test-endpoint:8080");
    Transaction transaction = createTestTransaction();

    // When: processing mix of local and peer transactions
    validator.validateTransaction(transaction, true, false); // local #1
    validator.validateTransaction(transaction, false, false); // peer #1
    validator.validateTransaction(transaction, true, false); // local #2
    validator.validateTransaction(transaction, false, false); // peer #2
    validator.validateTransaction(transaction, false, false); // peer #3
    validator.validateTransaction(transaction, true, false); // local #3

    // Then: statistics should be accurate
    assertThat(validator.getValidationCallCount()).isEqualTo(6);
    assertThat(validator.getLocalTransactionCount()).isEqualTo(3);
    assertThat(validator.getPeerTransactionCount()).isEqualTo(3);
  }

  @Test
  void shouldHandleTransactionsWithVariousGasPrices() {
    // Given: validator for testing different transaction types
    validator = new GrpcTransactionPoolValidator("test-endpoint:8080");

    // When: processing transactions with different gas prices
    Transaction lowGasTransaction = createTransactionWithGasPrice(Wei.of(1_000_000_000L));
    Transaction highGasTransaction = createTransactionWithGasPrice(Wei.of(100_000_000_000L));
    Transaction zeroGasTransaction = createTransactionWithGasPrice(Wei.ZERO);

    Optional<String> result1 = validator.validateTransaction(lowGasTransaction, true, false);
    Optional<String> result2 = validator.validateTransaction(highGasTransaction, true, false);
    Optional<String> result3 = validator.validateTransaction(zeroGasTransaction, true, false);

    // Then: all should be processed (fallback accepts all)
    assertThat(result1).isEmpty();
    assertThat(result2).isEmpty();
    assertThat(result3).isEmpty();
    assertThat(validator.getLocalTransactionCount()).isEqualTo(3);
  }

  @Test
  void shouldHandleTransactionsWithDifferentChainIds() {
    // Given: validator for testing different chain IDs
    validator = new GrpcTransactionPoolValidator("test-endpoint:8080");

    // When: processing transactions with different chain IDs
    Transaction mainnetTx = createTransactionWithChainId(BigInteger.valueOf(1));
    Transaction testnetTx = createTransactionWithChainId(BigInteger.valueOf(3));
    Transaction customTx = createTransactionWithChainId(BigInteger.valueOf(1337));

    Optional<String> result1 = validator.validateTransaction(mainnetTx, true, false);
    Optional<String> result2 = validator.validateTransaction(testnetTx, true, false);
    Optional<String> result3 = validator.validateTransaction(customTx, true, false);

    // Then: all should be processed
    assertThat(result1).isEmpty();
    assertThat(result2).isEmpty();
    assertThat(result3).isEmpty();
    assertThat(validator.getLocalTransactionCount()).isEqualTo(3);
  }

  @Test
  void shouldMaintainEndpointConfiguration() {
    // Given: validator with specific endpoint
    String testEndpoint = "grpc-service.example.com:9090";
    validator = new GrpcTransactionPoolValidator(testEndpoint);

    // When/Then: endpoint should be stored correctly
    assertThat(validator.getEndpoint()).isEqualTo(testEndpoint);
  }

  @Test
  void shouldHandlePriorityTransactionFlag() {
    // Given: validator for testing priority handling
    validator = new GrpcTransactionPoolValidator("test-endpoint:8080");
    Transaction transaction = createTestTransaction();

    // When: processing transactions with different priority flags
    Optional<String> normalResult = validator.validateTransaction(transaction, true, false);
    Optional<String> priorityResult = validator.validateTransaction(transaction, true, true);

    // Then: both should be processed (priority flag passed to implementation)
    assertThat(normalResult).isEmpty();
    assertThat(priorityResult).isEmpty();
    assertThat(validator.getLocalTransactionCount()).isEqualTo(2);
  }

  @Test
  void shouldHandleTransactionsWithComplexData() {
    // Given: validator for testing complex transaction data
    validator = new GrpcTransactionPoolValidator("test-endpoint:8080");

    // When: processing transaction with complex data
    Transaction complexTransaction =
        new TransactionTestFixture()
            .gasPrice(Wei.of(50_000_000_000L))
            .gasLimit(21000)
            .value(Wei.fromEth(1))
            .chainId(Optional.of(BigInteger.valueOf(1337)))
            .to(Optional.of(Address.fromHexString("0x742d35cc6634c0532925a3b8d039135682b2e78b")))
            .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());

    Optional<String> result = validator.validateTransaction(complexTransaction, true, false);

    // Then: should process successfully
    assertThat(result).isEmpty(); // Fallback accepts
    assertThat(validator.getLocalTransactionCount()).isEqualTo(1);

    // Verify transaction has expected properties
    assertThat(complexTransaction.getGasPrice()).isPresent();
    assertThat(complexTransaction.getChainId()).isPresent();
    assertThat(complexTransaction.getSender()).isNotNull();
    assertThat(complexTransaction.getHash()).isNotNull();
  }

  @Test
  void shouldResetStatisticsProperly() {
    // Given: validator with some processed transactions
    validator = new GrpcTransactionPoolValidator("test-endpoint:8080");
    Transaction transaction = createTestTransaction();

    // Process some transactions
    validator.validateTransaction(transaction, true, false);
    validator.validateTransaction(transaction, false, false);

    // When: creating new validator (simulates reset)
    validator.close();
    validator = new GrpcTransactionPoolValidator("test-endpoint:8080");

    // Then: statistics should start fresh
    assertThat(validator.getValidationCallCount()).isEqualTo(0);
    assertThat(validator.getLocalTransactionCount()).isEqualTo(0);
    assertThat(validator.getPeerTransactionCount()).isEqualTo(0);
  }

  private Transaction createTestTransaction() {
    return new TransactionTestFixture()
        .gasPrice(Wei.of(10_000_000_000L))
        .chainId(Optional.of(BigInteger.valueOf(1)))
        .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());
  }

  private Transaction createTransactionWithGasPrice(final Wei gasPrice) {
    return new TransactionTestFixture()
        .gasPrice(gasPrice)
        .chainId(Optional.of(BigInteger.valueOf(1)))
        .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());
  }

  private Transaction createTransactionWithChainId(final BigInteger chainId) {
    return new TransactionTestFixture()
        .gasPrice(Wei.of(10_000_000_000L))
        .chainId(Optional.of(chainId))
        .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());
  }
}
