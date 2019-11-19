package io.nuls.pocbft.utils.thread;

import io.nuls.base.RPCUtil;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.Block;
import io.nuls.base.data.NulsHash;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.exception.NulsException;
import io.nuls.core.rpc.util.NulsDateUtils;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.CommandConstant;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.message.VoteMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.round.MeetingMember;
import io.nuls.pocbft.model.bo.round.MeetingRound;
import io.nuls.pocbft.model.bo.vote.VoteResultData;
import io.nuls.pocbft.model.bo.vote.VoteResultItem;
import io.nuls.pocbft.rpc.call.CallMethodUtils;
import io.nuls.pocbft.rpc.call.NetWorkCall;
import io.nuls.pocbft.utils.manager.ConsensusManager;
import io.nuls.pocbft.utils.LoggerUtil;
import io.nuls.pocbft.utils.enumeration.ConsensusStatus;
import io.nuls.pocbft.utils.manager.RoundManager;
import io.nuls.pocbft.utils.manager.VoteManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static io.nuls.pocbft.cache.VoteCache.CURRENT_BLOCK_VOTE_DATA;

public class VoteProcessor implements Runnable {
    private RoundManager roundManager = SpringLiteContext.getBean(RoundManager.class);
    private Chain chain;
    public VoteProcessor(Chain chain){
        this.chain = chain;
    }

    @Override
    public void run() {
        while(true){
            try {
                //如果不是共识节点或则共识网络未组好则直接返回
                if (!chain.isPacker() || !chain.isNetworkState()) {
                    Thread.sleep(1000L);
                    continue;
                }
            }catch (Exception e){
                    chain.getLogger().error(e);
                    continue;
            }

            //等待上一轮投票处理完成
            while (!VoteCache.PRE_ROUND_CONFIRMED){
                try {
                    Thread.sleep(100L);
                }catch (InterruptedException e){
                    chain.getLogger().error(e);
                }
            }
            VoteCache.PRE_ROUND_CONFIRMED = false;
            /*
            * 当前区块如果已经提前被确认，
            * 如果确认的不是空块则直接通知区块模块该区块拜占庭已经完成等待区块被保存
            * 如果确认的空块这直接voteData
            * */
            if(VoteCache.CURRENT_BLOCK_VOTE_DATA.isFinished()){
                try {
                    VoteResultData stageTwoResult = VoteCache.CURRENT_BLOCK_VOTE_DATA.getFinalResult();
                    if(stageTwoResult.isConfirmedEmpty()){
                        VoteManager.switchEmptyVoteData(chain, VoteCache.CURRENT_BLOCK_VOTE_DATA.getRoundIndex(), VoteCache.CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound(), CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().getTime() + ConsensusConstant.VOTE_ROUND_INTERVAL_TIME);
                    }else{
                        noticeByzantineResult(stageTwoResult.getVoteResultItem());
                    }
                    continue;
                }catch (Exception e){
                    chain.getLogger().error(e);
                }
            }

            long time = VoteCache.CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().getTime();
            long currentRoundIndex = VoteCache.CURRENT_BLOCK_VOTE_DATA.getRoundIndex();
            int packIndex = VoteCache.CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound();
            /*
            * 1.判断当前轮次是否该本节点出块
            * 1.1.如果该本节点出块，则出块，等待投票结果收集
            * 1.2.如果不该本节点出块，则等待投票收集结果
            */
            MeetingRound consensusRound = roundManager.getCurrentRound(chain);
            MeetingMember member = consensusRound.getMyMember();
            if(member == null){
                chain.getLogger().warn("Current node is not a consensus node");
                continue;
            }
            boolean packing = checkConsensusStatus(chain)
                    && member.getRoundIndex() == VoteCache.CURRENT_BLOCK_VOTE_DATA.getRoundIndex()
                    && member.getPackingIndexOfRound() == VoteCache.CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound()
                    && VoteCache.CURRENT_BLOCK_VOTE_DATA.getVoteRound() == ConsensusConstant.VOTE_INIT_ROUND;

            long packEndTime = time + chain.getConfig().getPackingInterval();
            if(packing && NulsDateUtils.getCurrentTimeSeconds() < packEndTime){
                chain.getLogger().info("Node begins to block,roundIndex:{},packingIndex:{}",consensusRound.getIndex(),member.getPackingIndexOfRound());
                //todo 判断是否需要结算共识奖励
                boolean settleConsensusAward = false;
                packingBlock(chain, consensusRound, member, time,settleConsensusAward);
            }
            //如果本轮次投票已完成，则直接进入第二轮投票
            String packAddress = AddressTool.getStringAddressByBytes(member.getAgent().getPackingAddress());
            if(VoteCache.CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().isFinished()){
                stageTwoVote(chain, null, packAddress);
                continue;
            }
            //第一阶段投票及结果收集
            VoteResultData stageOneResult = stageOneVote(chain, packing, packEndTime, packAddress);
            //第二阶段投票及结果收集
            stageTwoVote(chain, stageOneResult, packAddress);
        }
    }

    private void packingBlock(Chain chain, MeetingRound round, MeetingMember member, long time, boolean settleConsensusAward){
        long start = System.currentTimeMillis();
        Block block;
        try {
            block = ConsensusManager.doPacking(chain, member, round, time, settleConsensusAward);
            if(block == null){
                return;
            }
            CallMethodUtils.receivePackingBlock(chain.getConfig().getChainId(), RPCUtil.encode(block.serialize()));
        }catch (Exception e){
            chain.getLogger().error("Packing exception");
            chain.getLogger().error(e);
            return;
        }
        VoteCache.CURRENT_BLOCK_VOTE_DATA.setBlockHash(block.getHeader().getHash());
        chain.getLogger().info("doPacking use:" + (System.currentTimeMillis() - start) + "ms" + "\n\n");
    }

    private boolean checkConsensusStatus(Chain chain){
        /*
        检查节点状态是否可打包(区块管理模块同步完成之后设置该状态)
        Check whether the node status can be packaged (set up after the block management module completes synchronization)
        */
        if (!chain.isCanPacking()) {
            return false;
        }
        return  chain.getConsensusStatus().ordinal() >= ConsensusStatus.RUNNING.ordinal();
    }

    /**
     * 第一阶段投票
     * @param chain         链信息
     * @param isPacker      是否为出块节点
     * @param packEndTime   当前区块打包结束时间
     * @param packAddress   本节点共识账户
     * */
    private VoteResultData stageOneVote(Chain chain, boolean isPacker, long packEndTime, String packAddress){
        if(!isPacker){
            long voteStageOneEndTime = packEndTime * 1000 + ConsensusConstant.VOTE_STAGE_ONE_WAIT_TIME;
            while(VoteCache.CURRENT_BLOCK_VOTE_DATA.getBlockHash() == null && NulsDateUtils.getCurrentTimeMillis()<= voteStageOneEndTime){
                try {
                    Thread.sleep(100);
                }catch(Exception e){
                    chain.getLogger().error(e);
                }
            }
        }
        //第一阶段投票，如果还没收到区块HASH则投空块，如果收到分叉块则投分叉，否则投正常区块
        VoteMessage stageOneVote = new VoteMessage(VoteCache.CURRENT_BLOCK_VOTE_DATA);
        stageOneVote.setVoteStage(ConsensusConstant.VOTE_STAGE_ONE);
        voteAndBroad(chain, packAddress, stageOneVote, ConsensusConstant.VOTE_STAGE_ONE);
        VoteResultData stageOneResult;
        try {
            long voteStageTwoEndTime = packEndTime * 1000 + ConsensusConstant.VOTE_STAGE_TOW_WAIT_TIME - NulsDateUtils.getCurrentTimeMillis();
            if(voteStageTwoEndTime < ConsensusConstant.WAIT_VOTE_RESULT_MIN_TIME){
                voteStageTwoEndTime = ConsensusConstant.WAIT_VOTE_RESULT_MIN_TIME;
            }
            stageOneResult = VoteCache.CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().get(VoteCache.CURRENT_BLOCK_VOTE_DATA.getVoteRound()).getStageOne().getVoteResult().get(voteStageTwoEndTime, TimeUnit.MILLISECONDS);
        }catch (Exception e){
            chain.getLogger().error(e);
            return null;
        }
        return stageOneResult;
    }

    /**
     * 第二阶段投票
     *
     * */
    private void stageTwoVote(Chain chain,VoteResultData stageOneResult, String packAddress){
        //如果本轮次投票以结束则直接处理投票结果
        if(!VoteCache.CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().isFinished()){
            VoteCache.CURRENT_BLOCK_VOTE_DATA.setVoteStage(ConsensusConstant.VOTE_STAGE_TWO);
            VoteMessage stageTwoVote = new VoteMessage(VoteCache.CURRENT_BLOCK_VOTE_DATA);
            stageTwoVote.setVoteStage(ConsensusConstant.VOTE_STAGE_TWO);
            if(stageOneResult == null || !stageOneResult.isResultSuccess() || stageOneResult.isConfirmedEmpty()){
                stageTwoVote.setBlockHash(NulsHash.EMPTY_NULS_HASH);
            }else{
                VoteResultItem voteResultItem = stageOneResult.getVoteResultItem();
                stageTwoVote.setBlockHash(voteResultItem.getBlockHash());
                if(voteResultItem.getFirstHeader() != null && voteResultItem.getSecondHeader() != null){
                    stageTwoVote.setFirstHeader(voteResultItem.getFirstHeader());
                    stageTwoVote.setSecondHeader(voteResultItem.getSecondHeader());
                }
            }
            voteAndBroad(chain, packAddress, stageTwoVote, ConsensusConstant.VOTE_STAGE_TWO);
        }
        try {
            //需一直等待第二阶段有结果返回
            VoteCache.CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().get(VoteCache.CURRENT_BLOCK_VOTE_DATA.getVoteRound()).getStageTwo().getVoteResult().get();
        }catch (Exception e){
            chain.getLogger().error(e);
        }

    }

    /**
     * 通知区块模块区块拜占庭完成
     * @param voteResultItem  拜占庭结果信息
     * */
    private void noticeByzantineResult(VoteResultItem voteResultItem){
        boolean bifurcate = voteResultItem.getFirstHeader() != null && voteResultItem.getSecondHeader()!= null;
        NulsHash firstHash = bifurcate ? voteResultItem.getFirstHeader().getHash() : voteResultItem.getBlockHash();
        NulsHash secondHash = bifurcate ? voteResultItem.getSecondHeader().getHash() : null;
        CallMethodUtils.noticeByzantineResult(chain, voteResultItem.getHeight(), bifurcate, firstHash, secondHash);
    }

    /**
     * 签名并广播投票消息
     * @param chain     链信息
     * @param address   签名账户地址
     * @param message   投票消息
     * */
    private void voteAndBroad(Chain chain, String address, VoteMessage message, byte stage) {
        //签名
        byte[] sign = new byte[0];
        try {
            sign = CallMethodUtils.signature(chain, address, NulsHash.calcHash(message.serializeForDigest()).getBytes());
        } catch (NulsException e) {
            LoggerUtil.commonLog.error(e);
        } catch (IOException e) {
            LoggerUtil.commonLog.error(e);
        }
        message.setSign(sign);
        message.setLocal(true);
        VoteCache.CURRENT_BLOCK_VOTE_DATA.isRepeatMessage(message.getVoteRound(), message.getVoteStage(), message.getAddress(chain));
        if(stage == ConsensusConstant.VOTE_STAGE_ONE){
            VoteCache.CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.offer(message);
            VoteCache.CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().getStageOne().getHaveVotedAccountSet().add(address);
        }else{
            VoteCache.CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.offer(message);
            VoteCache.CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().getStageTwo().getHaveVotedAccountSet().add(address);
        }
        NetWorkCall.broadcast(chain.getChainId(), message, CommandConstant.MESSAGE_VOTE,false);
    }
}
