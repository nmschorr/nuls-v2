package io.nuls.pocbft.utils.thread;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.message.VoteMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.utils.manager.VoteManager;

/**
 * 第二阶段投票收集器
 * Second stage voting collector
 *
 * @author tag
 * 2019/10/31
 */
public class StageOneVoteCollector implements Runnable {
    private Chain chain;
    public StageOneVoteCollector(Chain chain){
        this.chain = chain;
    }

    @Override
    public void run() {
        while (true) {
            try {
                VoteMessage message = VoteCache.CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.take();
                VoteManager.statisticalResult(chain, message, ConsensusConstant.VOTE_STAGE_ONE);
            } catch (Exception e) {
                chain.getLogger().error(e);
            }
        }
    }

}
