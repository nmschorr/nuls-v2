package io.nuls.pocbft.message.handler;

import io.nuls.base.RPCUtil;
import io.nuls.base.protocol.MessageProcessor;
import io.nuls.core.core.annotation.Component;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.CommandConstant;
import io.nuls.pocbft.message.VoteResultMessage;
/**
 * 投票结果数据消息处理器
 * Voting result data message processor
 *
 * @author tag
 * 2019/10/29
 */
@Component("VoteResultDataHandlerV1")
public class VoteResultHandler implements MessageProcessor {
    @Override
    public String getCmd() {
        return CommandConstant.MESSAGE_VOTE_RESULT;
    }

    @Override
    public void process(int chainId, String nodeId, String message) {
        VoteResultMessage voteResultMessage = RPCUtil.getInstanceRpcStr(message, VoteResultMessage.class);
        if (voteResultMessage == null) {
            return;
        }
        voteResultMessage.setNodeId(nodeId);
        VoteCache.VOTE_RESULT_MESSAGE_QUEUE.offer(voteResultMessage);
    }
}
