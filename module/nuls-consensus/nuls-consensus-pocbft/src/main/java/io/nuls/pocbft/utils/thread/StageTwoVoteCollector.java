package io.nuls.pocbft.utils.thread;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.message.VoteMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.utils.manager.VoteManager;

/**
 * 第一阶段投票收集器
 * First stage voting collector
 * @author tag
 * */
public class StageTwoVoteCollector implements Runnable{
    private Chain chain;
    public StageTwoVoteCollector(Chain chain){
        this.chain = chain;
    }

    @Override
    public void run() {
        while (true) {
            try {
                VoteMessage message = VoteCache.CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.take();
                VoteManager.statisticalResult(chain, message, ConsensusConstant.VOTE_STAGE_TWO);
            } catch (Exception e) {
                chain.getLogger().error(e);
            }
        }
    }
}
