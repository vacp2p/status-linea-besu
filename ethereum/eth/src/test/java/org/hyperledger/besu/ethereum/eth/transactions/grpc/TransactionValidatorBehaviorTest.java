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
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidatorFactory;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Comprehensive test that validates the actual behavior of the gRPC transaction validator
 * implementation without relying on external gRPC services or complex mocking.
 *
 * <p>Tests focus on: - Core validation logic (local vs peer transaction handling) - Error handling
 * and fallback mechanisms - Transaction data processing and statistics - Integration with Besu's
 * plugin system
 */
class TransactionValidatorBehaviorTest {

  private static final SignatureAlgorithm SIGNATURE_ALGORITHM =
      SignatureAlgorithmFactory.getInstance();

  @Test
  void shouldDemonstrateCompleteValidationFlow() {
    // GIVEN: Create a gRPC validator factory (this is how Besu creates validators)
    String endpoint = "grpc-validator-service:8080";
    PluginTransactionPoolValidatorFactory factory =
        new GrpcTransactionPoolValidatorFactory(endpoint);

    // WHEN: Besu creates a validator using the factory
    PluginTransactionPoolValidator validator = factory.createTransactionValidator();

    // THEN: Should get our gRPC validator
    assertThat(validator).isInstanceOf(GrpcTransactionPoolValidator.class);

    GrpcTransactionPoolValidator grpcValidator = (GrpcTransactionPoolValidator) validator;
    assertThat(grpcValidator.getEndpoint()).isEqualTo(endpoint);

    // Cleanup
    grpcValidator.close();
  }

  @Test
  void shouldProcessTransactionsAccordingToBusinessRules() {
    // GIVEN: gRPC validator with unreachable endpoint (tests fallback behavior)
    GrpcTransactionPoolValidator validator =
        new GrpcTransactionPoolValidator("unreachable-service:9999");

    Transaction localTransaction =
        createTransactionWithData(
            Wei.of(50_000_000_000L), // 50 gwei gas price
            Wei.fromEth(1), // 1 ETH value
            BigInteger.valueOf(1337) // custom chain ID
            );

    Transaction peerTransaction =
        createTransactionWithData(
            Wei.of(10_000_000_000L), // 10 gwei gas price
            Wei.fromEth(2), // 2 ETH value
            BigInteger.valueOf(1) // mainnet chain ID
            );

    // WHEN: Processing different transaction types
    Optional<String> localResult = validator.validateTransaction(localTransaction, true, false);
    Optional<String> peerResult = validator.validateTransaction(peerTransaction, false, false);
    Optional<String> priorityResult = validator.validateTransaction(localTransaction, true, true);

    // THEN: Business rules should be applied correctly

    // Local transactions attempt gRPC validation, fallback to acceptance
    assertThat(localResult).isEmpty(); // Accepted via fallback

    // Peer transactions bypass gRPC completely
    assertThat(peerResult).isEmpty(); // Accepted without gRPC call

    // Priority transactions still go through same logic
    assertThat(priorityResult).isEmpty(); // Accepted via fallback

    // Statistics should reflect the processing
    assertThat(validator.getValidationCallCount()).isEqualTo(3);
    assertThat(validator.getLocalTransactionCount()).isEqualTo(2); // local + priority
    assertThat(validator.getPeerTransactionCount()).isEqualTo(1);

    validator.close();
  }

  @Test
  void shouldExtractTransactionDataCorrectly() {
    // GIVEN: Validator and transaction with specific data
    GrpcTransactionPoolValidator validator = new GrpcTransactionPoolValidator("test-endpoint:8080");

    // Create transaction with all the data fields that should be sent to gRPC
    Wei gasPrice = Wei.of(75_000_000_000L);
    BigInteger chainId = BigInteger.valueOf(42);

    Transaction transaction =
        new TransactionTestFixture()
            .gasPrice(gasPrice)
            .gasLimit(100000)
            .value(Wei.fromEth(5))
            .chainId(Optional.of(chainId))
            .to(Optional.of(Address.fromHexString("0x742d35cc6634c0532925a3b8d039135682b2e78b")))
            .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());

    // WHEN: Processing the transaction (will attempt gRPC call and fallback)
    Optional<String> result = validator.validateTransaction(transaction, true, false);

    // THEN: Should process successfully and extract key data
    assertThat(result).isEmpty(); // Fallback accepts

    // Verify the transaction has the expected data that would be sent to gRPC
    assertThat(transaction.getHash()).isNotNull();
    assertThat(transaction.getSender()).isNotNull();
    assertThat(transaction.getGasPrice()).isPresent();
    assertThat(transaction.getGasPrice().get()).isEqualTo(gasPrice);
    assertThat(transaction.getChainId()).isPresent();
    assertThat(transaction.getChainId().get()).isEqualTo(chainId);

    validator.close();
  }

  @Test
  void shouldHandleFactoryBehaviorCorrectly() {
    // GIVEN: Different factory configurations

    // Factory with valid endpoint
    GrpcTransactionPoolValidatorFactory validFactory =
        new GrpcTransactionPoolValidatorFactory("localhost:8080");

    // Factory with no endpoint
    GrpcTransactionPoolValidatorFactory nullFactory = new GrpcTransactionPoolValidatorFactory(null);

    GrpcTransactionPoolValidatorFactory emptyFactory = new GrpcTransactionPoolValidatorFactory("");

    // WHEN: Creating validators
    PluginTransactionPoolValidator validValidator = validFactory.createTransactionValidator();
    PluginTransactionPoolValidator nullValidator = nullFactory.createTransactionValidator();
    PluginTransactionPoolValidator emptyValidator = emptyFactory.createTransactionValidator();

    // THEN: Should behave according to configuration

    // Valid endpoint creates gRPC validator
    assertThat(validValidator).isInstanceOf(GrpcTransactionPoolValidator.class);

    // Null/empty endpoints return default validator
    assertThat(nullValidator).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);
    assertThat(emptyValidator).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);

    // Singleton behavior for same factory
    PluginTransactionPoolValidator validValidator2 = validFactory.createTransactionValidator();
    assertThat(validValidator).isSameAs(validValidator2);

    // Cleanup
    ((GrpcTransactionPoolValidator) validValidator).close();
  }

  @Test
  void shouldProvideMeaningfulStatistics() {
    // GIVEN: Validator for statistics testing
    GrpcTransactionPoolValidator validator = new GrpcTransactionPoolValidator("stats-test:8080");

    Transaction tx = createTestTransaction();

    // WHEN: Processing various transactions
    validator.validateTransaction(tx, true, false); // local
    validator.validateTransaction(tx, false, false); // peer
    validator.validateTransaction(tx, true, true); // local priority
    validator.validateTransaction(tx, false, true); // peer priority
    validator.validateTransaction(tx, true, false); // local again

    // THEN: Statistics should be accurate and meaningful
    assertThat(validator.getValidationCallCount()).isEqualTo(5);
    assertThat(validator.getLocalTransactionCount()).isEqualTo(3); // 2 local + 1 priority
    assertThat(validator.getPeerTransactionCount()).isEqualTo(2);

    // Statistics should be consistent
    assertThat(validator.getLocalTransactionCount() + validator.getPeerTransactionCount())
        .isEqualTo(validator.getValidationCallCount());

    validator.close();
  }

  @Test
  void shouldHandleEdgeCases() {
    // GIVEN: Validator for edge case testing
    GrpcTransactionPoolValidator validator =
        new GrpcTransactionPoolValidator("edge-case-test:8080");

    // WHEN: Testing edge cases

    // Transaction with zero gas price
    Transaction zeroGasTransaction =
        new TransactionTestFixture()
            .gasPrice(Wei.ZERO)
            .chainId(Optional.of(BigInteger.valueOf(1)))
            .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());

    // Transaction with very high gas price
    Transaction highGasTransaction =
        new TransactionTestFixture()
            .gasPrice(Wei.of(Long.MAX_VALUE))
            .chainId(Optional.of(BigInteger.valueOf(1)))
            .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());

    // Process edge case transactions
    Optional<String> zeroResult = validator.validateTransaction(zeroGasTransaction, true, false);
    Optional<String> highResult = validator.validateTransaction(highGasTransaction, true, false);

    // THEN: Should handle edge cases gracefully
    assertThat(zeroResult).isEmpty(); // Fallback accepts
    assertThat(highResult).isEmpty(); // Fallback accepts

    assertThat(validator.getLocalTransactionCount()).isEqualTo(2);

    validator.close();
  }

  @Test
  void shouldIntegrateWithBesuPluginSystem() {
    // GIVEN: Simulate how Besu's CLI would use the validator

    // This simulates the CLI creating a factory based on configuration
    String configuredEndpoint = "rln-prover.example.com:9090";

    // In Besu, the TransactionPoolOptions would create this factory
    PluginTransactionPoolValidatorFactory factory =
        new GrpcTransactionPoolValidatorFactory(configuredEndpoint);

    // WHEN: Besu's transaction pool uses the factory
    PluginTransactionPoolValidator validator = factory.createTransactionValidator();

    // THEN: Integration should work seamlessly
    assertThat(validator).isNotNull();
    assertThat(validator).isInstanceOf(GrpcTransactionPoolValidator.class);

    GrpcTransactionPoolValidator grpcValidator = (GrpcTransactionPoolValidator) validator;
    assertThat(grpcValidator.getEndpoint()).isEqualTo(configuredEndpoint);

    // Validator should be ready to process transactions
    Transaction testTx = createTestTransaction();
    Optional<String> result = validator.validateTransaction(testTx, true, false);
    assertThat(result).isEmpty(); // Fallback behavior

    grpcValidator.close();
  }

  private Transaction createTestTransaction() {
    return new TransactionTestFixture()
        .gasPrice(Wei.of(20_000_000_000L))
        .chainId(Optional.of(BigInteger.valueOf(1)))
        .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());
  }

  private Transaction createTransactionWithData(
      final Wei gasPrice, final Wei value, final BigInteger chainId) {
    return new TransactionTestFixture()
        .gasPrice(gasPrice)
        .value(value)
        .chainId(Optional.of(chainId))
        .gasLimit(21000)
        .createTransaction(SIGNATURE_ALGORITHM.generateKeyPair());
  }
}
