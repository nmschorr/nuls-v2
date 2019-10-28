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
package io.nuls.network.rpc.cmd;

import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.model.StringUtils;
import io.nuls.core.rpc.cmd.BaseCmd;
import io.nuls.core.rpc.model.*;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.network.cfg.NetworkConfig;
import io.nuls.network.constant.CmdConstant;
import io.nuls.network.constant.NetworkErrorCode;
import io.nuls.network.constant.NodeConnectStatusEnum;
import io.nuls.network.manager.ConnectionManager;
import io.nuls.network.manager.NodeGroupManager;
import io.nuls.network.model.Node;
import io.nuls.network.model.NodeGroup;
import io.nuls.network.utils.IpUtil;
import io.nuls.network.utils.LoggerUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lan
 * @description Open peer connection remote call node rpc
 * 直接连接peer
 * @create 2019/10/18
 **/
@Component
public class ConnectRpc extends BaseCmd {
    private NodeGroupManager nodeGroupManager = NodeGroupManager.getInstance();
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    @Autowired
    NetworkConfig networkConfig;

    @CmdAnnotation(cmd = CmdConstant.CMD_DIRECT_CONNECT_NODES, version = 1.0,
            description = "连接节点")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterValidRange = "[1-65535]", parameterDes = "连接的链Id,取值区间[1-65535]"),
            @Parameter(parameterName = "nodeId", requestType = @TypeDescriptor(value = String.class), parameterDes = "节点组ID，ip:port")
    })
    @ResponseData(name = "返回值", description = "返回一个Map对象", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = Boolean.class, description = "无法规定时间完成业务连接的返回false")
    }))
    public Response connectNodes(Map params) {
        int chainId = Integer.valueOf(String.valueOf(params.get("chainId")));
        boolean blCross = Boolean.valueOf(String.valueOf(params.get("isCross")));
        Map<String, Object> rtMap = new HashMap<>(1);
        try {
            String nodeId = String.valueOf(params.get("nodeId"));
            if (chainId < 0 || StringUtils.isBlank(nodeId)) {
                return failed(NetworkErrorCode.PARAMETER_ERROR);
            }
            NodeGroup nodeGroup = nodeGroupManager.getNodeGroupByChainId(chainId);
            String[] ipPort = IpUtil.changeHostToIp(nodeId);
            if (null == ipPort) {
                return failed(NetworkErrorCode.PARAMETER_ERROR);
            }
            if (blCross) {
                //暂时不支持跨链连接
                return failed(NetworkErrorCode.PARAMETER_ERROR);
            } else {
                if (nodeGroup.getLocalNetNodeContainer().hadPeerIp(nodeId, ipPort[0])) {
                    return success(rtMap.put("value", true));
                }
                Node node = new Node(nodeGroup.getMagicNumber(), ipPort[0], Integer.valueOf(ipPort[1]), 0, Node.OUT, blCross);
                node.setConnectStatus(NodeConnectStatusEnum.CONNECTING);

                node.setRegisterListener(() -> LoggerUtil.logger(chainId).debug("new node {} Register!", node.getId()));

                node.setConnectedListener(() -> {
                    LoggerUtil.logger(chainId).debug("connected success:{},iscross={}", node.getId(), node.isCrossConnect());
                    connectionManager.nodeClientConnectSuccess(node);
                });

                node.setDisconnectListener(() -> {
                    LoggerUtil.logger(node.getNodeGroup().getChainId()).debug("connected disconnect:{},iscross={}", node.getId(), node.isCrossConnect());
                    connectionManager.nodeConnectDisconnect(node);
                });
                if (connectionManager.connection(node)) {
                    //等待监听1s后,返回信息TODO:
//                   CompletableFuture<Node> future = new CompletableFuture<>();
//                   Node result =future.get(1000,TimeUnit.MILLISECONDS);
                    int times = 0;
                    boolean hadConn = false;
                    while (!hadConn && times < 10) {
                        Thread.sleep(100L);
                        hadConn = nodeGroup.getLocalNetNodeContainer().hadPeerIp(nodeId, ipPort[0]);
                        times++;
                    }
                    success(rtMap.put("value", hadConn));
                }

            }
        } catch (Exception e) {
            LoggerUtil.logger(chainId).error(e);
            return failed(e.getMessage());
        }
        return success(rtMap.put("value", false));
    }


}
