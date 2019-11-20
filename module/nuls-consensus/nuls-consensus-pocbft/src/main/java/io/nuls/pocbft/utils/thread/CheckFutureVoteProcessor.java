package io.nuls.pocbft.utils.thread;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.CommandConstant;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.message.GetVoteResultMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.vote.VoteData;
import io.nuls.pocbft.model.bo.vote.VoteResultData;
import io.nuls.pocbft.model.bo.vote.VoteRoundData;
import io.nuls.pocbft.rpc.call.NetWorkCall;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
                long roundIndex = VoteCache.CURRENT_BLOCK_VOTE_DATA.getRoundIndex();
                long height = VoteCache.CURRENT_BLOCK_VOTE_DATA.getHeight();
                byte voteRound = VoteCache.CURRENT_BLOCK_VOTE_DATA.getVoteRound();
                int packIndexOfRound = VoteCache.CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound();
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
                Set<String> invalidNodes = new HashSet<>();
                if(!VoteCache.FUTURE_VOTE_DATA.isEmpty()){
                    GetVoteResultMessage getVoteResultMessage = new GetVoteResultMessage();
                    getVoteResultMessage.setHeight(height);
                    getVoteResultMessage.setRoundIndex(roundIndex);
                    getVoteResultMessage.setPackingIndexOfRound(packIndexOfRound);
                    getVoteResultMessage.setVoteRound(voteRound);
                    for (VoteData voteData:VoteCache.FUTURE_VOTE_DATA.values()) {
                        for (VoteRoundData voteRoundData:voteData.getVoteRoundMap().values()) {
                            for (String nodeId : voteRoundData.getVoteNodeSet()) {
                                if(!invalidNodes.contains(nodeId)){
                                    NetWorkCall.sendToNode(chain.getChainId(), getVoteResultMessage, nodeId, CommandConstant.MESSAGE_GET_VOTE_RESULT);
                                    invalidNodes.add(nodeId);
                                    if(VoteCache.CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().getStageTwo().getVoteResult().get(ConsensusConstant.GET_DATA_TIME_OUT, TimeUnit.SECONDS) != null){
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }


            }catch (Exception e){
                chain.getLogger().error(e);
            }
        }
    }
}
