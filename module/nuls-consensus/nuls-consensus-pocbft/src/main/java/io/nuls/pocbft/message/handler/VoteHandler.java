package io.nuls.pocbft.message.handler;
import io.nuls.base.RPCUtil;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.protocol.MessageProcessor;
import io.nuls.base.signture.BlockSignature;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.exception.NulsException;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.CommandConstant;
import io.nuls.pocbft.message.VoteMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.utils.LoggerUtil;
import io.nuls.pocbft.utils.enumeration.VoteTime;
import io.nuls.pocbft.utils.manager.ChainManager;
import io.nuls.pocbft.utils.manager.RoundManager;

import java.io.IOException;


/**
 * 投票消息处理器
 * Voting message processor
 *
 * @author tag
 * 2019/10/28
 */
@Component("VoteHandlerV1")
public class VoteHandler implements MessageProcessor {
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private RoundManager roundManager;
    @Override
    public String getCmd() {
        return CommandConstant.MESSAGE_VOTE;
    }

    @Override
    public void process(int chainId, String nodeId, String msg) {
        Chain chain = chainManager.getChainMap().get(chainId);
        if(chain == null){
            LoggerUtil.commonLog.error("Chains do not exist");
            return;
        }
        VoteMessage message = RPCUtil.getInstanceRpcStr(msg, VoteMessage.class);
        if (message == null) {
            return;
        }

        VoteTime voteTime = VoteCache.CURRENT_BLOCK_VOTE_DATA.voteTime(message);
        //收到之前的投票信息直接忽略
        if(voteTime == VoteTime.PREVIOUS){
            chain.getLogger().warn("This is the previous vote");
            return;
        }

        // 验证签名
        BlockSignature signature = new BlockSignature();
        try {
            signature.parse(message.getSign(), 0);
            if (signature.verifySignature(message.getVoteHash()).isFailed()) {
                chain.getLogger().error("Voting signature verification failed");
                return;
            }
        } catch (NulsException e) {
            chain.getLogger().error(e);
            return;
        } catch (IOException e) {
            chain.getLogger().error(e);
            return;
        }
        message.setAddress(AddressTool.getStringAddressByBytes(AddressTool.getAddress(signature.getPublicKey(), chainId)));

        if(voteTime == VoteTime.CURRENT_STAGE_ONE){
            boolean isRepeatMessage = VoteCache.CURRENT_BLOCK_VOTE_DATA.isRepeatMessage(message.getVoteRound(), message.getVoteStage(), message.getAddress(chain));
            //判断是否收到过该消息，如果收到过则直接返回
            if(isRepeatMessage){
                chain.getLogger().warn("Repeated voting");
               return;
            }
            VoteCache.CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.offer(message);
        }else if(voteTime == VoteTime.CURRENT_STAGE_TWO){
            boolean isRepeatMessage = VoteCache.CURRENT_BLOCK_VOTE_DATA.isRepeatMessage(message.getVoteRound(), message.getVoteStage(), message.getAddress(chain));
            //判断是否收到过该消息，如果收到过则直接返回
            if(isRepeatMessage){
                chain.getLogger().warn("Repeated voting");
                return;
            }
            VoteCache.CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.offer(message);
        }else{
            String consensusKey = message.getConsensusKey();
            //如果为当前确认区块之后轮次的投票信息
            if(consensusKey.equals(VoteCache.CURRENT_BLOCK_VOTE_DATA.getConsensusKey())){
                VoteCache.CURRENT_BLOCK_VOTE_DATA.addVoteMessage(chain, message, nodeId);
            }else{
                VoteCache.addFutureCache(chain, message, nodeId);
            }
        }
    }
}
