package io.nuls.pocbft.utils.thread;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.model.bo.Chain;

import java.util.concurrent.TimeUnit;

/**
 * 检测是否有未来消息线程
 * Detect if there are future message threads
 * */
public class CheckFutureVoteProcessor implements Runnable{
    private Chain chain;

    public CheckFutureVoteProcessor(Chain chain){
        this.chain = chain;
    }

    @Override
    public void run() {
        while (true) {
            try {
                //判断当前投票轮次是否已出结果
                if(VoteCache.CURRENT_BLOCK_VOTE_DATA.isFinished() || VoteCache.CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().isFinished()){
                    chain.getLogger().warn("The current voting round has received results");
                    Thread.sleep(100);
                }

                /*
                * 判断是否收到本区块之后区块的投票信息，如果收到过则向节点获取本区块的最终投票结果数据
                * 如果没有过之后区块的投票信息，则判断是否收到过当前投票轮次的投票数据，如果收到过则向
                * 节点获取本投票轮次的投票结果数据
                * */

            }catch (Exception e){
                chain.getLogger().error(e);
            }
        }
    }
}
