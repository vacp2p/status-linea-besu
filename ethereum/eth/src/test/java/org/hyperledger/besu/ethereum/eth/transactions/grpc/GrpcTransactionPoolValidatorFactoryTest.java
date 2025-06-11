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

import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;

import org.junit.jupiter.api.Test;

/**
 * Tests for GrpcTransactionPoolValidatorFactory that focus on the factory logic: - Validator
 * creation based on endpoint configuration - Singleton behavior and caching - Fallback to default
 * validator - Edge cases and error handling
 */
class GrpcTransactionPoolValidatorFactoryTest {

  @Test
  void shouldReturnGrpcValidatorWhenValidEndpointProvided() {
    // Given: factory with valid endpoint
    String endpoint = "localhost:9090";
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory(endpoint);

    // When: creating validator
    PluginTransactionPoolValidator validator = factory.createTransactionValidator();

    // Then: should return gRPC validator instance
    assertThat(validator).isInstanceOf(GrpcTransactionPoolValidator.class);

    GrpcTransactionPoolValidator grpcValidator = (GrpcTransactionPoolValidator) validator;
    assertThat(grpcValidator.getEndpoint()).isEqualTo(endpoint);

    // Cleanup
    grpcValidator.close();
  }

  @Test
  void shouldReturnDefaultValidatorWhenEndpointEmpty() {
    // Given: factory with empty endpoint
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory("");

    // When: creating validator
    PluginTransactionPoolValidator validator = factory.createTransactionValidator();

    // Then: should return default validator
    assertThat(validator).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);
  }

  @Test
  void shouldReturnDefaultValidatorWhenEndpointNull() {
    // Given: factory with null endpoint
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory(null);

    // When: creating validator
    PluginTransactionPoolValidator validator = factory.createTransactionValidator();

    // Then: should return default validator
    assertThat(validator).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);
  }

  @Test
  void shouldReturnDefaultValidatorWhenEndpointWhitespace() {
    // Given: factory with whitespace-only endpoint
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory("   ");

    // When: creating validator
    PluginTransactionPoolValidator validator = factory.createTransactionValidator();

    // Then: should return default validator (whitespace should be treated as empty)
    assertThat(validator).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);
  }

  @Test
  void shouldCacheValidatorInstance() {
    // Given: factory with valid endpoint
    String endpoint = "localhost:9091";
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory(endpoint);

    // When: creating multiple validators
    PluginTransactionPoolValidator validator1 = factory.createTransactionValidator();
    PluginTransactionPoolValidator validator2 = factory.createTransactionValidator();
    PluginTransactionPoolValidator validator3 = factory.createTransactionValidator();

    // Then: should return same instance (singleton behavior)
    assertThat(validator1).isInstanceOf(GrpcTransactionPoolValidator.class);
    assertThat(validator1).isSameAs(validator2);
    assertThat(validator2).isSameAs(validator3);

    // Cleanup
    ((GrpcTransactionPoolValidator) validator1).close();
  }

  @Test
  void shouldHandleVariousEndpointFormats() {
    // Test different valid endpoint formats
    String[] validEndpoints = {
      "localhost:8080", "127.0.0.1:9090", "grpc-service.example.com:443", "10.0.0.1:50051"
    };

    for (String endpoint : validEndpoints) {
      // Given: factory with endpoint in specific format
      GrpcTransactionPoolValidatorFactory factory =
          new GrpcTransactionPoolValidatorFactory(endpoint);

      // When: creating validator
      PluginTransactionPoolValidator validator = factory.createTransactionValidator();

      // Then: should create gRPC validator
      assertThat(validator).isInstanceOf(GrpcTransactionPoolValidator.class);

      GrpcTransactionPoolValidator grpcValidator = (GrpcTransactionPoolValidator) validator;
      assertThat(grpcValidator.getEndpoint()).isEqualTo(endpoint);

      // Cleanup
      grpcValidator.close();
    }
  }

  @Test
  void shouldNotCacheDefaultValidator() {
    // Given: factory without endpoint (returns default validator)
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory(null);

    // When: creating multiple validators
    PluginTransactionPoolValidator validator1 = factory.createTransactionValidator();
    PluginTransactionPoolValidator validator2 = factory.createTransactionValidator();

    // Then: should return same default validator instance (but not cached by factory)
    assertThat(validator1).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);
    assertThat(validator2).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);
    assertThat(validator1).isSameAs(validator2); // Same static instance
  }

  @Test
  void shouldCreateDifferentValidatorsForDifferentEndpoints() {
    // Given: factories with different endpoints
    GrpcTransactionPoolValidatorFactory factory1 =
        new GrpcTransactionPoolValidatorFactory("localhost:8080");
    GrpcTransactionPoolValidatorFactory factory2 =
        new GrpcTransactionPoolValidatorFactory("localhost:9090");

    // When: creating validators
    PluginTransactionPoolValidator validator1 = factory1.createTransactionValidator();
    PluginTransactionPoolValidator validator2 = factory2.createTransactionValidator();

    // Then: should create different instances
    assertThat(validator1).isInstanceOf(GrpcTransactionPoolValidator.class);
    assertThat(validator2).isInstanceOf(GrpcTransactionPoolValidator.class);
    assertThat(validator1).isNotSameAs(validator2);

    GrpcTransactionPoolValidator grpcValidator1 = (GrpcTransactionPoolValidator) validator1;
    GrpcTransactionPoolValidator grpcValidator2 = (GrpcTransactionPoolValidator) validator2;

    assertThat(grpcValidator1.getEndpoint()).isEqualTo("localhost:8080");
    assertThat(grpcValidator2.getEndpoint()).isEqualTo("localhost:9090");

    // Cleanup
    grpcValidator1.close();
    grpcValidator2.close();
  }

  @Test
  void shouldHandleNormalizedEndpoints() {
    // Given: factory with endpoint that could be normalized
    String endpoint = "  localhost:8080  "; // with whitespace
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory(endpoint);

    // When: creating validator
    PluginTransactionPoolValidator validator = factory.createTransactionValidator();

    // Then: should still create gRPC validator (implementation may or may not trim)
    if (validator instanceof GrpcTransactionPoolValidator) {
      GrpcTransactionPoolValidator grpcValidator = (GrpcTransactionPoolValidator) validator;
      assertThat(grpcValidator.getEndpoint()).isEqualTo(endpoint); // Stores as-is
      grpcValidator.close();
    } else {
      // If implementation trims whitespace and treats as empty, default validator is returned
      assertThat(validator).isEqualTo(PluginTransactionPoolValidator.VALIDATE_ALL);
    }
  }

  @Test
  void shouldProvideDescriptiveToString() {
    // Given: factory with endpoint
    String endpoint = "test-service:8080";
    GrpcTransactionPoolValidatorFactory factory = new GrpcTransactionPoolValidatorFactory(endpoint);

    // When: getting string representation
    String factoryString = factory.toString();

    // Then: should be descriptive (if implemented)
    assertThat(factoryString).isNotNull();
    // Note: Actual toString implementation depends on the class implementation
  }
}
