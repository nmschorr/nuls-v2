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
     * @param  consensusAddr,
     * @param updateType      1 增加  2 删除
     * @description 更新共识列表, 增加或者减少节点时候调用. 只知道地址时候就只给地址
     * @return
     */
    boolean updateConsensusList(int chainId, String consensusPubKey, String consensusAddr,short updateType);

    /**
     * @param chainId
     * @param selfPubKey
     * @param selfPrivKey
     * @param consensusPubKeyList
     * @param consensusAddrList
     * @description 在成为共识节点时候调用，有公钥的，不用在地址列表里。如果只有共识节点地址的，可以给地址列表consensusAddrList
     * @return
     */
    boolean initConsensusNetwork(int chainId, String selfPubKey, String selfPrivKey, List<String> consensusPubKeyList, List<String> consensusAddrList);

    /**
     * @param chainId
     * @description 自身从共识节点变为普通节点时候调用
     */
    void cleanConsensusNetwork(int chainId);

    /**
     * 广播共识消息
     *
     * @param chainId
     * @param cmd
     * @param messageBodyHex
     * @return
     */
    List<String> broadCastConsensusNet(int chainId, String cmd, String messageBodyHex);


    ConsensusKeys getSelfConsensusKeys(int chainId);

    ConsensusNet getConsensusNode(int chainId, ConsensusNet consensusNet);

    boolean updateConsensusNode(int chainId, ConsensusNet consensusNet, boolean isConnect);

    void printTestInfo();

    boolean netStatusChange(Chain chain);

    boolean getNetStatus(Chain chain);

}
