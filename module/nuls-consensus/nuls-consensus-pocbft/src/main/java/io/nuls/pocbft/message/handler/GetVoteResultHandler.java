package io.nuls.pocbft.message.handler;

import io.nuls.base.RPCUtil;
import io.nuls.base.protocol.MessageProcessor;
import io.nuls.core.core.annotation.Component;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.CommandConstant;
import io.nuls.pocbft.message.GetVoteResultMessage;
import io.nuls.pocbft.message.VoteMessage;
import io.nuls.pocbft.message.VoteResultMessage;
import io.nuls.pocbft.model.bo.vote.VoteResultData;
import io.nuls.pocbft.rpc.call.NetWorkCall;

/**
 * 获取投票结果数据消息处理器
 * Get voting result data message processor
 *
 * @author tag
 * 2019/10/29
 */
@Component("GetVoteResultDataHandlerV1")
public class GetVoteResultHandler implements MessageProcessor {
    @Override
    public String getCmd() {
        return CommandConstant.MESSAGE_GET_VOTE_RESULT;
    }

    @Override
    public void process(int chainId, String nodeId, String message) {
        GetVoteResultMessage getVoteResultMessage = RPCUtil.getInstanceRpcStr(message, VoteMessage.class);
        if (getVoteResultMessage == null) {
            return;
        }
        VoteResultData resultData = VoteCache.CONFIRMED_VOTE_RESULT_MAP.get(getVoteResultMessage.getVoteRoundKey()).get(getVoteResultMessage.getVoteRound());
        //从本地缓存中查询该投票结果信息并返回
        VoteResultMessage voteResultMessage = new VoteResultMessage();
        voteResultMessage.setVoteResultData(resultData);
        NetWorkCall.sendToNode(chainId, voteResultMessage, nodeId, CommandConstant.MESSAGE_VOTE_RESULT);
    }
}
