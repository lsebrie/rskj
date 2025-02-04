/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.mine;

import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FamilyUtils.class})
public class BlockToMineBuilderTest {

    private BlockToMineBuilder blockBuilder;
    private BlockValidationRule validationRules;

    @Before
    public void setUp() {
        validationRules = mock(BlockValidationRule.class);

        RepositoryLocator repositoryLocator = mock(RepositoryLocator.class);
        StateRootHandler stateRootHandler = mock(StateRootHandler.class);
        MiningConfig miningConfig = mock(MiningConfig.class);
        DifficultyCalculator difficultyCalculator = mock(DifficultyCalculator.class);
        MinimumGasPriceCalculator minimumGasPriceCalculator = mock(MinimumGasPriceCalculator.class);
        MinerUtils minerUtils = mock(MinerUtils.class);

        blockBuilder = new BlockToMineBuilder(
                mock(ActivationConfig.class),
                miningConfig,
                repositoryLocator,
                mock(BlockStore.class),
                mock(TransactionPool.class),
                difficultyCalculator,
                new GasLimitCalculator(Constants.mainnet()),
                new ForkDetectionDataCalculator(),
                validationRules,
                mock(MinerClock.class),
                new BlockFactory(ActivationConfigsForTest.all()),
                mock(BlockExecutor.class),
                minimumGasPriceCalculator,
                minerUtils
        );

        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        Repository snapshot = mock(Repository.class);
        GasLimitConfig gasLimitConfig = new GasLimitConfig(0,0,false);

        when(minerUtils.getAllTransactions(any())).thenReturn(new ArrayList<>());
        when(minerUtils.filterTransactions(any(), any(), any(), any(), any())).thenReturn(new ArrayList<>());
        when(repositoryLocator.snapshotAt(any())).thenReturn(snapshot);
        when(minimumGasPriceCalculator.calculate(any())).thenReturn(mock(Coin.class));
        when(stateRootHandler.translate(any())).thenReturn(TestUtils.randomHash());
        when(miningConfig.getGasLimit()).thenReturn(gasLimitConfig);
        when(miningConfig.getUncleListLimit()).thenReturn(10);
        when(miningConfig.getCoinbaseAddress()).thenReturn(TestUtils.randomAddress());
        when(difficultyCalculator.calcDifficulty(any(), any())).thenReturn(blockDifficulty);
    }

    @Test
    public void BuildBlockHasEmptyUnclesWhenCreateAnInvalidBlock() {
        BlockHeader parent = buildBlockHeaderWithSibling();

        when(validationRules.isValid(any())).thenReturn(false);

        Block nextBLock = blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

        assertThat(nextBLock.getUncleList(), empty());
    }

    @Test
    public void BuildBlockHasUnclesWhenCreateAnInvalidBlock() {
        BlockHeader parent = buildBlockHeaderWithSibling();

        when(validationRules.isValid(any())).thenReturn(true);

        Block nextBLock = blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

        assertThat(nextBLock.getUncleList(), hasSize(1));
    }

    private BlockHeader buildBlockHeaderWithSibling() {
        BlockHeader blockHeader = mock(BlockHeader.class);
        long blockNumber = 42L;
        when(blockHeader.getNumber()).thenReturn(blockNumber);
        Keccak256 blockHash = TestUtils.randomHash();
        when(blockHeader.getHash()).thenReturn(blockHash);
        when(blockHeader.getMinimumGasPrice()).thenReturn(mock(Coin.class));
        when(blockHeader.getGasLimit()).thenReturn(new byte[0]);

        mockBlockFamily(blockNumber, blockHash, createBlockHeader());

        return blockHeader;
    }

    private void mockBlockFamily(long blockNumber, Keccak256 blockHash, BlockHeader relative) {
        PowerMockito.mockStatic(FamilyUtils.class);
        PowerMockito.when(FamilyUtils.getUnclesHeaders(any(), eq(blockNumber + 1L), eq(blockHash), anyInt()))
                .thenReturn(Collections.singletonList(relative));
    }

    private BlockHeader createBlockHeader() {
        return new BlockHeader(
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, TestUtils.randomAddress(),
                EMPTY_TRIE_HASH, null, EMPTY_TRIE_HASH,
                new Bloom().getData(), BlockDifficulty.ZERO, 1L,
                EMPTY_BYTE_ARRAY, 0L, 0L, EMPTY_BYTE_ARRAY, Coin.ZERO,
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY,
                Coin.ZERO, 0, false, true, false
        );
    }
}