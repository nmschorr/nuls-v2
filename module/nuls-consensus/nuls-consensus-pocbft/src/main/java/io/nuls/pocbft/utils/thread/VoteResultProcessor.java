package io.nuls.pocbft.utils.thread;

import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.message.VoteResultMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.round.MeetingRound;
import io.nuls.pocbft.model.bo.vote.VoteResultData;
import io.nuls.pocbft.model.bo.vote.VoteResultItem;
import io.nuls.pocbft.utils.manager.RoundManager;
import io.nuls.pocbft.utils.manager.VoteManager;

/**
 * 投票结果处理器
 * Voting result processor
 * @author tag
 * */
public class VoteResultProcessor implements Runnable{
    private Chain chain;
    public VoteResultProcessor(Chain chain){
        this.chain = chain;
    }
    private RoundManager roundManager = SpringLiteContext.getBean(RoundManager.class);
    @Override
    public void run() {
        while (true){
            try {
                VoteResultMessage voteResultMessage = VoteCache.VOTE_RESULT_MESSAGE_QUEUE.take();
                //验证投票结果正确性
                VoteResultData voteResultData = voteResultMessage.getVoteResultData();
                VoteResultItem voteInfo = voteResultData.getVoteResultItem();
                if(voteInfo.getVoteStage() == ConsensusConstant.VOTE_STAGE_ONE){
                    chain.getLogger().warn("Receive the first stage voting result information");
                    continue;
                }
                if(voteInfo.getHeight() < chain.getNewestHeader().getHeight()){
                    chain.getLogger().warn("Block confirmation result data before receiving");
                    continue;
                }
                /*
                * 接收投票结果又两种方式
                * 1.接收到新区块，首先会进行基础验证，基础验证中会生成并验证轮次，如果验证不通过不会像其他节点获取区块拜占庭验证结果
                * 2.向其他节点获取到本轮次投票结果
                * 以上两种情况都是轮次已经生成验证过的，所以如果接收到轮次不存在的投票结果直接丢弃
                * */
                MeetingRound round = roundManager.getRoundByIndex(chain, voteInfo.getRoundIndex());
                if(round == null){
                    chain.getLogger().warn("The voting result round does not exist");
                    continue;
                }
                VoteManager.verifyVoteResult(chain, voteResultData, round);
            }catch (Exception e){
                chain.getLogger().error(e);
            }
        }

    }
}
