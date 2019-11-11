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
package io.nuls.pocnetwork.model;

import io.nuls.core.crypto.HexUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author lanjinsheng
 * @date 2019/10/17
 * @description
 */
public class ConsensusNetGroup {
    public ConsensusNetGroup(int chainId) {
        this.chainId = chainId;
    }

    private int chainId;
    private Map<String, ConsensusNet> group = new ConcurrentHashMap<>();

    public int getChainId() {
        return chainId;
    }

    public void setChainId(int chainId) {
        this.chainId = chainId;
    }

    public Map<String, ConsensusNet> getGroup() {
        return group;
    }

    public void setGroup(Map<String, ConsensusNet> group) {
        this.group = group;
    }

    public void addConsensus(ConsensusNet consensusNet) {
        group.put(HexUtil.encode(consensusNet.getPubKey()), consensusNet);
    }

    public List<String> getConsensusNetIps() {
        List<String> ips = new ArrayList<>();
        for (Map.Entry<String, ConsensusNet> entry : group.entrySet()) {
            if (null != entry.getValue().getNodeId()) {
                try {
                    ips.add(entry.getValue().getNodeId().split(":")[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return ips;
    }

    public String removeConsensus(byte[] consensusPubKey) {
        String key = HexUtil.encode(consensusPubKey);
        ConsensusNet consensusNet= group.get(key);
        if(null != consensusNet){
          return null;
        }
        group.remove(key);
       return consensusNet.getNodeId();
    }
}
