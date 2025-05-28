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

import org.hyperledger.besu.crypto.SECPSignature;

import java.math.BigInteger;
import java.util.stream.Collectors;

import net.vac.prover.AccessListEntries;
import net.vac.prover.AccessListEntry;
import net.vac.prover.Address;
import net.vac.prover.SendTransactionRequest;
import net.vac.prover.StorageKey;
import net.vac.prover.TransactionType;
import net.vac.prover.U256;
import net.vac.prover.VersionedHash;
import net.vac.prover.Wei;

public class TransactionMapper {

  public static SendTransactionRequest toRequest(
      final org.hyperledger.besu.ethereum.core.Transaction tx) {
    var builder =
        SendTransactionRequest.newBuilder()
            .setForCopy(false)
            .setTransactionType(mapTransactionType(tx.getType()))
            .setNonce(tx.getNonce())
            .setGasLimit(tx.getGasLimit())
            .setValue(createWei(tx.getValue()))
            .setSignature(createSignature(tx.getSignature()))
            .setPayload(com.google.protobuf.ByteString.copyFrom(tx.getPayload().toArrayUnsafe()))
            .setSender(createAddress(tx.getSender()));

    tx.getGasPrice().ifPresent(gp -> builder.setGasPrice(createWei(gp)));
    tx.getMaxPriorityFeePerGas().ifPresent(mpf -> builder.setMaxPriorityFeePerGas(createWei(mpf)));
    tx.getMaxFeePerGas().ifPresent(mf -> builder.setMaxFeePerGas(createWei(mf)));
    tx.getMaxFeePerBlobGas().ifPresent(mfb -> builder.setMaxFeePerBlobGas(createWei(mfb)));
    tx.getTo().ifPresent(to -> builder.setTo(createAddress(to)));
    tx.getChainId().ifPresent(cid -> builder.setChainId(createU256(cid)));

    tx.getAccessList()
        .ifPresent(
            accessList ->
                builder.addAllMaybeAccessList(
                    accessList.stream()
                        .map(
                            entry ->
                                AccessListEntries.newBuilder()
                                    .addEntries(
                                        AccessListEntry.newBuilder()
                                            .setAddress(createAddress(entry.address()))
                                            .addAllStorageKeys(
                                                entry.storageKeys().stream()
                                                    .map(
                                                        key ->
                                                            StorageKey.newBuilder()
                                                                .setValue(
                                                                    com.google.protobuf.ByteString
                                                                        .copyFrom(
                                                                            key.toArrayUnsafe()))
                                                                .build())
                                                    .collect(Collectors.toList()))
                                            .build())
                                    .build())
                        .collect(Collectors.toList())));

    tx.getVersionedHashes()
        .ifPresent(
            hashes ->
                builder.addAllVersionedHashes(
                    hashes.stream()
                        .map(
                            hash ->
                                VersionedHash.newBuilder()
                                    .setValue(
                                        com.google.protobuf.ByteString.copyFrom(
                                            hash.toBytes().toArrayUnsafe()))
                                    .build())
                        .collect(Collectors.toList())));

    return builder.build();
  }

  private static TransactionType mapTransactionType(
      final org.hyperledger.besu.datatypes.TransactionType type) {
    return switch (type) {
      case FRONTIER -> TransactionType.FRONTIER;
      case ACCESS_LIST -> TransactionType.ACCESS_LIST;
      case EIP1559 -> TransactionType.EIP1559;
      case BLOB -> TransactionType.BLOB;
      case DELEGATE_CODE -> TransactionType.DELEGATE_CODE;
    };
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

  private static net.vac.prover.SECPSignature createSignature(final SECPSignature besuSignature) {
    return net.vac.prover.SECPSignature.newBuilder()
        .setValue(
            com.google.protobuf.ByteString.copyFrom(besuSignature.encodedBytes().toArrayUnsafe()))
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
