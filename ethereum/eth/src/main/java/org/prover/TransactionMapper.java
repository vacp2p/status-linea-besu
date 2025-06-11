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

import java.math.BigInteger;

import net.vac.prover.Address;
import net.vac.prover.SendTransactionRequest;
import net.vac.prover.U256;
import net.vac.prover.Wei;

public class TransactionMapper {

  public static SendTransactionRequest toRequest(
      final org.hyperledger.besu.ethereum.core.Transaction tx) {
    var builder = SendTransactionRequest.newBuilder();

    // Set optional gasPrice if present
    tx.getGasPrice().ifPresent(gp -> builder.setGasPrice(createWei(gp)));

    // Set optional sender if present
    builder.setSender(createAddress(tx.getSender()));

    // Set optional chainId if present
    tx.getChainId().ifPresent(cid -> builder.setChainId(createU256(cid)));

    // Set transaction hash (calculate from transaction)
    builder.setTransactionHash(
        com.google.protobuf.ByteString.copyFrom(tx.getHash().toArrayUnsafe()));

    return builder.build();
  }

  private static Wei createWei(final org.hyperledger.besu.datatypes.Wei besuWei) {
    return Wei.newBuilder()
        .setValue(com.google.protobuf.ByteString.copyFrom(besuWei.toBytes().toArrayUnsafe()))
        .build();
  }

  private static Address createAddress(final org.hyperledger.besu.datatypes.Address besuAddress) {
    return Address.newBuilder()
        .setValue(com.google.protobuf.ByteString.copyFrom(besuAddress.toArrayUnsafe()))
        .build();
  }

  private static U256 createU256(final BigInteger bigInteger) {
    byte[] bytes = bigInteger.toByteArray();
    byte[] u256Bytes = new byte[32];

    if (bytes.length <= 32) {
      System.arraycopy(bytes, 0, u256Bytes, 32 - bytes.length, bytes.length);
    } else {
      System.arraycopy(bytes, bytes.length - 32, u256Bytes, 0, 32);
    }

    return U256.newBuilder().setValue(com.google.protobuf.ByteString.copyFrom(u256Bytes)).build();
  }
}
