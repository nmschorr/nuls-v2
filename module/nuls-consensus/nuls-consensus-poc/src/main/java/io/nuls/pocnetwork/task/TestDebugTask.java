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
package io.nuls.pocnetwork.task;

import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.poc.utils.LoggerUtil;
import io.nuls.pocnetwork.service.ConsensusNetService;

/**
 * 测试 定时打印连接信息
 *
 * @author lan
 * @create 2018/11/14
 */
public class TestDebugTask implements Runnable {
    static String pub1 = "025cf6d48cf00d875cde55fd9bbe5176d16f927991f6c7aac07890d091f16ca941";
    static String pub2 = "02214fed44362ee44b2ef690a133437ea91b4d6f94d16e653fb3d1dea3d13d88bb";
    static String pub3 = "02632a4768a9c4b5bd5dc4f3de0361eaf09aff127fa6eb2cf9dbac403776efd8e3";

    @Override
    public void run() {
        printlnInfos();
    }

    private void printlnInfos() {
        LoggerUtil.commonLog.debug("TestDebugTask printlnInfos====");
        ConsensusNetService consensusNetService = SpringLiteContext.getBean(ConsensusNetService.class);
        consensusNetService.printTestInfo();
    }


}
