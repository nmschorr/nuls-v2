/*
 * MIT License
 *
 * Copyright (c) 2017-2019 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.pocnetwork.service;

import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocnetwork.model.ConsensusKeys;
import io.nuls.pocnetwork.model.ConsensusNet;

import java.util.List;

/**
 * @author lanjinsheng
 * @date 2019/10/17
 * @description
 */
public interface ConsensusNetService {
    /**
     * @param consensusPubKey
     * @param updateType      1 增加  2 删除
     * @description 更新共识列表, 增加或者减少节点时候调用
     */
    boolean updateConsensusList(int chainId, String consensusPubKey, short updateType);

    /**
     * @param chainId
     * @param selfPubKey
     * @param selfPrivKey
     * @param consensusPubKeyList
     * @return
     */
    boolean initConsensusNetwork(int chainId, String selfPubKey, String selfPrivKey, List<String> consensusPubKeyList);

    /**
     * 广播共识消息
     *
     * @param chainId
     * @param cmd
     * @param messageBodyHex
     * @return
     */
    List<String> broadCastConsensusNet(int chainId, String cmd, String messageBodyHex);

    /**
     * @param chainId
     */
    void cleanConsensusNetwork(int chainId);

    ConsensusKeys getSelfConsensusKeys(int chainId);

    boolean isConsensusNode(int chainId, ConsensusNet consensusNet);

    boolean updateConsensusNode(int chainId, ConsensusNet consensusNet, boolean isConnect);

    void printTestInfo();

    boolean netStatusChange(Chain chain);
    boolean getNetStatus(Chain chain);

}
