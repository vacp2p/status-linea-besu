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

import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidator;
import org.hyperledger.besu.plugin.services.txvalidator.PluginTransactionPoolValidatorFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for creating gRPC transaction pool validators. */
public class GrpcTransactionPoolValidatorFactory implements PluginTransactionPoolValidatorFactory {

  private static final Logger LOG =
      LoggerFactory.getLogger(GrpcTransactionPoolValidatorFactory.class);

  private final String endpoint;
  private volatile GrpcTransactionPoolValidator validator;

  /**
   * Creates a new factory.
   *
   * @param endpoint the gRPC endpoint in format "host:port"
   */
  public GrpcTransactionPoolValidatorFactory(final String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public PluginTransactionPoolValidator createTransactionValidator() {
    if (endpoint != null && !endpoint.trim().isEmpty()) {
      if (validator == null) {
        synchronized (this) {
          if (validator == null) {
            LOG.info("Creating gRPC transaction pool validator for endpoint: {}", endpoint);
            validator = new GrpcTransactionPoolValidator(endpoint);
          }
        }
      }
      return validator;
    } else {
      LOG.debug("No gRPC endpoint configured, using default validator");
      return PluginTransactionPoolValidator.VALIDATE_ALL;
    }
  }
}
