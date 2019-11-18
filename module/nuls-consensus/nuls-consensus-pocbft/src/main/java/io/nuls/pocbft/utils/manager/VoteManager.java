package io.nuls.pocbft.utils.manager;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.BlockExtendsData;
import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.NulsHash;
import io.nuls.base.signture.BlockSignature;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.exception.NulsException;
import io.nuls.pocbft.cache.VoteCache;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.message.VoteMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.round.MeetingRound;
import io.nuls.pocbft.model.bo.vote.VoteResultData;
import io.nuls.pocbft.model.bo.vote.VoteResultItem;
import io.nuls.pocbft.model.bo.vote.VoteData;
import io.nuls.pocbft.model.bo.vote.VoteRoundData;
import io.nuls.pocbft.model.bo.vote.VoteStageData;
import io.nuls.pocbft.rpc.call.CallMethodUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.nuls.pocbft.cache.VoteCache.*;

/**
 * 投票信息管理类
 * Voting information management
 *
 * @author tag
 * 2019/10/28
 */
@Component
public class VoteManager {
    private static Lock switchVoteInfoLock = new ReentrantLock();
    @Autowired
    private static RoundManager roundManager;

    /**
     * 统计投票结果，接收到当前阶段投票信息时
     */
    public static void statisticalResult(Chain chain, VoteMessage message, byte voteStage) throws Exception {
        //如果当前区块已经确认完成或则当前轮次确认完成则直接返回
        if (CURRENT_BLOCK_VOTE_DATA.isFinished() || CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().isFinished()) {
            chain.getLogger().warn("The current round of voting has been confirmed to be completed");
            return;
        }
        //如果不为当前轮次投票信息则直接返回
        if (message.getRoundIndex() != CURRENT_BLOCK_VOTE_DATA.getRoundIndex() || message.getPackingIndexOfRound() != CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound()
                || message.getVoteStage() < CURRENT_BLOCK_VOTE_DATA.getVoteStage() || message.getHeight() != message.getHeight()) {
            chain.getLogger().error("Voting information error");
            return;
        }
        //验证投票是否为共识节点投票
        MeetingRound meetingRound = roundManager.getRound(chain, CURRENT_BLOCK_VOTE_DATA.getRoundIndex(), CURRENT_BLOCK_VOTE_DATA.getRoundStartTime());
        if (!meetingRound.getMemberAddressList().contains(message.getAddress(chain))) {
            chain.getLogger().error("Current voting is not consensus node voting");
            return;
        }
        VoteStageData stageData = CURRENT_BLOCK_VOTE_DATA.getCurrentVoteRoundStageData(voteStage);
        //如果为第一阶段投票，不需要保存签名数据
        NulsHash voteHash = message.getVoteHash();
        if (voteStage == ConsensusConstant.VOTE_STAGE_TWO) {
            if (!stageData.getItemMap().containsKey(voteHash)) {
                VoteResultItem voteResultItem = new VoteResultItem(message);
                List<byte[]> signList = new ArrayList<>();
                signList.add(message.getSign());
                stageData.getItemVoteCountMap().put(voteHash, 1);
                voteResultItem.setSignatureList(signList);
                stageData.getItemMap().put(voteHash, voteResultItem);
            } else {
                stageData.getItemMap().get(voteHash).getSignatureList().add(message.getSign());
                int voteCount = stageData.getItemVoteCountMap().get(voteHash);
                voteCount++;
                stageData.getItemVoteCountMap().put(voteHash, voteCount);
            }
        } else {
            if (stageData.getItemVoteCountMap().containsKey(voteHash)) {
                stageData.getItemVoteCountMap().put(voteHash, 1);
            } else {
                int voteCount = stageData.getItemVoteCountMap().get(voteHash);
                voteCount++;
                stageData.getItemVoteCountMap().put(voteHash, voteCount);
            }
        }
        int voteTotalCount = 0;
        int maxRateCount = 0;
        for (Map.Entry<NulsHash, Integer> entry : stageData.getItemVoteCountMap().entrySet()) {
            int value = entry.getValue();
            if (maxRateCount < value) {
                maxRateCount = value;
            }
            voteTotalCount += value;
        }
        //如果收集到的签名数量小于最小拜占庭验证数则直接返回
        if (voteTotalCount < CURRENT_BLOCK_VOTE_DATA.getMinByzantineCount()) {
            return;
        }
        VoteResultData voteResultData = null;
        if (maxRateCount >= CURRENT_BLOCK_VOTE_DATA.getMinPassCount()) {
            voteResultData = new VoteResultData();
            VoteResultItem item = stageData.getItemMap().get(voteHash);
            voteResultData.getVoteResultItemList().add(item);
            voteResultData.setConfirmedEmpty(item.isConfirmedEmpty());
            voteResultData.setResultSuccess(true);
        }
        int otherCount = voteTotalCount - maxRateCount;
        if (maxRateCount >= CURRENT_BLOCK_VOTE_DATA.getMinCoverCount() && otherCount >= CURRENT_BLOCK_VOTE_DATA.getMinCoverCount()) {
            voteResultData = new VoteResultData();
            voteResultData.setResultSuccess(false);
            voteResultData.getVoteResultItemList().addAll(stageData.getItemMap().values());
        }
        if (voteResultData != null) {
            handleVoteResult(chain, CURRENT_BLOCK_VOTE_DATA.getRoundIndex(), CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound(), CURRENT_BLOCK_VOTE_DATA.getVoteRound(), voteStage, voteResultData);
        }
    }

    /**
     * 向其他节点获取当前区块投票结果
     * 1.收到下一个区块的投票信息
     * 2.区块模块收到其他节点当前区块确认完成消息时
     */
    public static void getCurrentBlockResult() {

    }

    /**
     * 投票结果验证
     * Verification of voting results
     * 1.投票签名正确性验证
     * 2.投票签名拜占庭验证
     *
     * @param chain          链信息
     * @param voteResultData 投票结果数据
     * @param round          轮次信息
     **/
    public static boolean verifyVoteResult(Chain chain, VoteResultData voteResultData, MeetingRound round) {
        //是否为正常确认区块
        boolean isConfirmBlock = voteResultData.getVoteResultItemList().size() == 0;
        //该投票结果是否为当前区块投票结果
        VoteResultItem voteResultBasicInfo = voteResultData.getVoteResultItem();
        int agentCount = round.getMemberCount();
        int byzantineRate = chain.getConfig().getByzantineRate();
        int minPassCount = agentCount * byzantineRate / ConsensusConstant.VALUE_OF_ONE_HUNDRED + 1;
        if (isConfirmBlock) {
            int voteCount = voteResultBasicInfo.getSignatureList().size();
            if (voteCount < minPassCount) {
                chain.getLogger().error("Block winning rate is less than the minimum passing rate");
                return false;
            }
            voteResultData.setResultSuccess(true);
            voteResultData.setConfirmedEmpty(voteResultBasicInfo.isConfirmedEmpty());
        } else {
            int coverRate = ConsensusConstant.VALUE_OF_ONE_HUNDRED - byzantineRate;
            int minCoverCount = coverRate * 2 * agentCount / ConsensusConstant.VALUE_OF_ONE_HUNDRED;
            int minByzantineCount = minPassCount < minCoverCount ? minPassCount : minCoverCount;

            int voteTotalCount = 0;
            int maxItemCount = 0;
            for (VoteResultItem item : voteResultData.getVoteResultItemList()) {
                int itemCount = item.getSignatureList().size();
                if (itemCount > maxItemCount) {
                    maxItemCount = itemCount;
                }
                voteTotalCount += itemCount;
            }
            int otherCount = voteTotalCount - maxItemCount;
            if (maxItemCount < minByzantineCount || otherCount < minByzantineCount) {
                chain.getLogger().error("Byzantine verification error of voting result data");
                return false;
            }
            voteResultData.setResultSuccess(false);
        }
        if (!verifySignature(chain, voteResultData, round)) {
            return false;
        }
        handleVoteResult(chain, voteResultBasicInfo.getRoundIndex(), voteResultBasicInfo.getPackingIndexOfRound(), voteResultBasicInfo.getVoteRound(), ConsensusConstant.VOTE_STAGE_TWO, voteResultData);
        return true;
    }

    /**
     * 投票结果签名验证
     * Voting result signature verification
     *
     * @param chain          链信息
     * @param voteResultData 投票结果数据
     * @param round          轮次信息
     */
    private static boolean verifySignature(Chain chain, VoteResultData voteResultData, MeetingRound round) {
        List<String> memberAddressList = round.getMemberAddressList();
        for (VoteResultItem item : voteResultData.getVoteResultItemList()) {
            NulsHash voteHash;
            try {
                voteHash = NulsHash.calcHash(item.serializeForDigest());
            } catch (IOException e) {
                chain.getLogger().error(e);
                return false;
            }
            for (byte[] sign : item.getSignatureList()) {
                BlockSignature signature = new BlockSignature();
                try {
                    signature.parse(sign, 0);
                } catch (NulsException e) {
                    chain.getLogger().error(e);
                    return false;
                }
                String address = AddressTool.getStringAddressByBytes(AddressTool.getAddress(signature.getPublicKey(), chain.getChainId()));
                if (!memberAddressList.contains(address)) {
                    chain.getLogger().error("Not a consensus node signature");
                    return false;
                }
                if (signature.verifySignature(voteHash).isFailed()) {
                    chain.getLogger().error("Voting signature verification failed");
                    return false;
                }

            }
        }
        return true;
    }

    /**
     * 设置指定阶段投票结果（为啥同步？避免在切换投票轮次的同时有另外的线程来设置投票结果）
     *
     * @param chain          链信息
     * @param roundIndex     轮次
     * @param packIndex      出块下标
     * @param voteStage      投票阶段
     * @param voteRound      投票轮次
     * @param voteResultData 投票结果
     */
    private static synchronized void handleVoteResult(Chain chain, long roundIndex, int packIndex, byte voteRound, byte voteStage, VoteResultData voteResultData) {
        byte resultTime = resultTime(roundIndex, packIndex, voteRound);
        //如果为之前轮次投票结果，直接忽略
        if (resultTime == ConsensusConstant.PREVIOUS_ROUND) {
            chain.getLogger().warn("The voting result is the result of previous rounds");
        } else if (resultTime == ConsensusConstant.CURRENT_ROUND) {
            handleCurrentRoundResult(chain, roundIndex, packIndex, voteRound, voteStage, voteResultData);
        } else if (resultTime == ConsensusConstant.CURRENT_BLOCK) {
            handleCurrentBlockResult(chain, roundIndex, packIndex, voteRound, voteResultData);
        } else {
            //如果收到未来区块的投票结果则该投票结果则该区块一定是被正常确认
            if (voteResultData.getVoteResultItemList().size() != 1 && voteResultData.getVoteResultItem().isConfirmedEmpty()) {
                chain.getLogger().warn("Voting result data exception");
                return;
            }
            VoteResultItem voteResultItem = voteResultData.getVoteResultItem();
            //缓存投票结果
            String resultConsensusKey = voteResultItem.getConsensusKey();
            addVoteResult(resultConsensusKey, ConsensusConstant.FINAL_VOTE_ROUND_SIGN, voteResultData);
            //通知区块模块区块拜占庭验证完成，等待区块保存，切换voteData
            noticeByzantineResult(chain, voteResultItem);
        }
    }

    /**
     * 处理当前投票轮次结果消息
     *
     * @param chain          链信息
     * @param roundIndex     轮次
     * @param packIndex      出块下标
     * @param voteRound      投票轮次
     * @param voteStage      投票阶段
     * @param voteResultData 投票结果数据
     */
    private static void handleCurrentRoundResult(Chain chain, long roundIndex, int packIndex, byte voteRound, byte voteStage, VoteResultData voteResultData) {
        if (CURRENT_BLOCK_VOTE_DATA.isFinished() || CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().isFinished()) {
            chain.getLogger().warn("End of current voting round");
            return;
        }
        //如果为第一阶段投票，则直接返回投票结果
        if (voteStage == ConsensusConstant.VOTE_STAGE_ONE) {
            CURRENT_BLOCK_VOTE_DATA.setVoteResult(voteRound, voteStage, voteResultData);
            return;
        }
        String currentVoteKey = roundIndex + ConsensusConstant.SEPARATOR + packIndex;
        /*
         * 1.确认空块，则直接切换voteData
         * 2.确认正常块/分叉，则通知区块模块，区块拜占庭验证成功，等待区块保存的时候切换voteData
         * 3.确认失败，切换投票轮次进入下一轮投票
         * */
        if (voteResultData.isResultSuccess()) {
            //如果统计出结果，则缓存本轮次投票结果为当前区块最终通票结果
            addVoteResult(currentVoteKey, ConsensusConstant.FINAL_VOTE_ROUND_SIGN, voteResultData);
            if (voteResultData.isConfirmedEmpty()) {
                CURRENT_BLOCK_VOTE_DATA.setVoteResult(voteRound, voteStage, voteResultData);
                long nextVoteTime = CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().getTime() + ConsensusConstant.VOTE_ROUND_INTERVAL_TIME;
                switchEmptyVoteData(chain, roundIndex, packIndex, nextVoteTime);
            } else {
                noticeByzantineResult(chain, voteResultData.getVoteResultItem());
            }
        } else {
            addVoteResult(currentVoteKey, voteRound, voteResultData);
            CURRENT_BLOCK_VOTE_DATA.setVoteResult(voteRound, voteStage, voteResultData);
            switchVoteRound(chain, roundIndex, packIndex, CURRENT_BLOCK_VOTE_DATA.getVoteRound(), CURRENT_BLOCK_VOTE_DATA.getCurrentRoundData().getTime());
        }
    }

    /**
     * 处理当前投票轮次结果消息
     *
     * @param chain          链信息
     * @param roundIndex     轮次
     * @param packIndex      出块下标
     * @param voteRound      投票轮次
     * @param voteResultData 投票结果数据
     */
    private static void handleCurrentBlockResult(Chain chain, long roundIndex, int packIndex, byte voteRound, VoteResultData voteResultData) {
        CURRENT_BLOCK_VOTE_DATA.setVoteResult(voteResultData.getVoteResultItem().getVoteRound(), ConsensusConstant.VOTE_STAGE_TWO, voteResultData);
        String currentVoteKey = roundIndex + ConsensusConstant.SEPARATOR + packIndex;
        if (voteResultData.isResultSuccess()) {
            addVoteResult(currentVoteKey, ConsensusConstant.FINAL_VOTE_ROUND_SIGN, voteResultData);
            if (voteResultData.isConfirmedEmpty()) {
                CURRENT_BLOCK_VOTE_DATA.setVoteResult(voteRound, ConsensusConstant.VOTE_STAGE_TWO, voteResultData);
                long nextVoteTime = voteResultData.getVoteResultItem().getTime() + ConsensusConstant.VOTE_ROUND_INTERVAL_TIME;
                switchEmptyVoteData(chain, roundIndex, packIndex, nextVoteTime);
            } else {
                noticeByzantineResult(chain, voteResultData.getVoteResultItem());
            }
        } else {
            addVoteResult(currentVoteKey, voteRound, voteResultData);
            CURRENT_BLOCK_VOTE_DATA.setVoteResult(voteRound, ConsensusConstant.VOTE_STAGE_TWO, voteResultData);
            switchVoteRound(chain, roundIndex, packIndex, voteResultData.getVoteResultItem().getVoteRound(), voteResultData.getVoteResultItem().getTime());
        }
    }


    /**
     * 区块被正常确认后保存时切换当前确认区块
     *
     * @param chain       链信息
     * @param blockHeader 区块头
     */
    public static void switchBlockVoteData(Chain chain, BlockHeader blockHeader) {
        try {
            switchVoteInfoLock.lock();
            BlockExtendsData blockExtendsData = blockHeader.getExtendsData();

            //保存区块之前，起投票结果一定会先被缓存好了的
            String consensusKey = blockExtendsData.getRoundIndex() + ConsensusConstant.SEPARATOR + blockExtendsData.getPackingIndexOfRound();
            VoteResultData voteResultData = CONFIRMED_VOTE_RESULT_MAP.get(consensusKey).get(ConsensusConstant.FINAL_VOTE_ROUND_SIGN);
            VoteResultItem voteResultItem = voteResultData.getVoteResultItem();

            int blockPackIndex = blockExtendsData.getPackingIndexOfRound();
            int agentCount = blockExtendsData.getConsensusMemberCount();
            int nextPackIndex = blockPackIndex + 1;
            long nextRoundIndex = blockExtendsData.getRoundIndex();
            long nextHeight = blockHeader.getHeight() + 1;
            //下一个区块投票开始时间是本区块最后一轮投票时间 + 投票轮次间隔时间
            long nextVoteTime = voteResultItem.getTime() + ConsensusConstant.VOTE_ROUND_INTERVAL_TIME;
            if (blockPackIndex == agentCount) {
                //判断是否有该区块轮次信息，如果没有则生成
                try {
                    MeetingRound round = roundManager.getRound(chain, nextRoundIndex, nextVoteTime);
                    if (round.getMemberCount() != agentCount || round.getStartTime() != blockExtendsData.getRoundStartTime()) {
                        chain.getLogger().warn("Block round information is different from the locally calculated round information");
                    }
                    nextRoundIndex += 1;
                    nextPackIndex = ConsensusConstant.INIT_PACING_INDEX;
                }catch (NulsException e){
                    chain.getLogger().error(e);
                    return;
                }
            }
            //中断当前投票轮次
            CURRENT_BLOCK_VOTE_DATA.setVoteResult(CURRENT_BLOCK_VOTE_DATA.getVoteRound(), ConsensusConstant.VOTE_STAGE_TWO, voteResultData);
            switchVoteData(chain, nextRoundIndex, nextPackIndex, nextVoteTime, agentCount, nextHeight);
        } finally {
            switchVoteInfoLock.unlock();
        }
    }

    /**
     * 切换当前确认区块，当前区块被确认为空块时调用
     *
     * @param chain             链信息
     * @param currentRoundIndex 当前轮次信息
     * @param currentPackIndex  当前出块下标
     */
    public static void switchEmptyVoteData(Chain chain, long currentRoundIndex, int currentPackIndex, long nextVoteTime) {
        try {
            switchVoteInfoLock.lock();
            //切换当前投票数据
            long nextRoundIndex = currentRoundIndex;
            int nextPackIndex = currentPackIndex + 1;
            int agentCount = CURRENT_BLOCK_VOTE_DATA.getAgentCount();
            long nextHeight = CURRENT_BLOCK_VOTE_DATA.getHeight();
            if (currentPackIndex == agentCount) {
                //切换共识轮次信息，共识轮次开始时间为当前投票轮次时间+2S,修改出块节点数agentCount
                try {
                    MeetingRound newRound = roundManager.getRound(chain, nextRoundIndex + 1, nextVoteTime);
                    agentCount = newRound.getMemberCount();
                    nextRoundIndex = newRound.getIndex();
                    nextPackIndex = ConsensusConstant.INIT_PACING_INDEX;
                }catch (NulsException e){
                    chain.getLogger().error(e);
                    return;
                }
            }
            switchVoteData(chain, nextRoundIndex, nextPackIndex, nextVoteTime, agentCount, nextHeight);
        } finally {
            switchVoteInfoLock.unlock();
        }
    }

    /**
     * 切换voteData
     *
     * @param chain          链信息
     * @param nextRoundIndex 下一个确认区块所在轮次
     * @param nextPackIndex  下一个确认区块出块下标
     * @param nextVoteTime   下一个区块投票开始时间
     * @param agentCount     下一个区块所在轮次共识节点数
     * @param nextHeight     下一个区块高度
     */
    private static void switchVoteData(Chain chain, long nextRoundIndex, int nextPackIndex, long nextVoteTime, int agentCount, long nextHeight) {
        boolean isSwitch = nextRoundIndex > CURRENT_BLOCK_VOTE_DATA.getRoundIndex()
                || (nextRoundIndex == CURRENT_BLOCK_VOTE_DATA.getRoundIndex() && nextPackIndex > CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound());
        if (!isSwitch) {
            chain.getLogger().warn("Current round switched");
            return;
        }
        String voteKey = nextRoundIndex + ConsensusConstant.SEPARATOR + nextPackIndex;
        VoteData nextVoteData = null;
        //如果收到过下一个区块的投票信息，则直接从缓存中获取下一个区块的投票信息，并修改节点数量，否则新建下一个区块的投票信息对象
        if (FUTURE_VOTE_DATA.containsKey(voteKey)) {
            nextVoteData = FUTURE_VOTE_DATA.remove(voteKey);
            //如果缓存中投票高度与本地计算的高度不一致，则直接删除缓存中数据
            if (nextVoteData.getHeight() != nextHeight || nextVoteData.getRoundStartTime() != nextVoteTime) {
                chain.getLogger().warn("Data exception in cache");
                nextVoteData = null;
            } else {
                //设置投票相关信息
                nextVoteData.setAgentCount(agentCount);
            }
        }
        if (nextVoteData == null) {
            nextVoteData = new VoteData(nextRoundIndex, nextPackIndex, agentCount, nextHeight, nextVoteTime);
        }
        if (agentCount != CURRENT_BLOCK_VOTE_DATA.getAgentCount()) {
            int byzantineRate = chain.getConfig().getByzantineRate();
            int coverRate = ConsensusConstant.VALUE_OF_ONE_HUNDRED - byzantineRate;
            int minPassCount = agentCount * byzantineRate / ConsensusConstant.VALUE_OF_ONE_HUNDRED + 1;
            int minCoverCount = coverRate * 2 * agentCount / ConsensusConstant.VALUE_OF_ONE_HUNDRED;
            int minByzantineCount = minPassCount < minCoverCount ? minPassCount : minCoverCount;
            nextVoteData.setMinPassCount(minPassCount);
            nextVoteData.setMinByzantineCount(minByzantineCount);
            nextVoteData.setMinCoverCount(minCoverCount);
        } else {
            nextVoteData.setMinByzantineCount(CURRENT_BLOCK_VOTE_DATA.getMinByzantineCount());
            nextVoteData.setMinPassCount(CURRENT_BLOCK_VOTE_DATA.getMinPassCount());
            nextVoteData.setMinCoverCount(CURRENT_BLOCK_VOTE_DATA.getMinCoverCount());
        }
        //切换确认区块
        switchVoteDataMessage(chain, ConsensusConstant.VOTE_INIT_ROUND, nextVoteTime, nextVoteData);
        PRE_ROUND_CONFIRMED = true;
    }

    /**
     * 切换当前区块投票轮次，本轮区块确认失败需进入下一轮投票
     * Switch the current block voting round. If the block confirmation fails in this round, you need to enter the next round of voting
     *
     * @param chain             链信息
     * @param currentRoundIndex 当前轮次信息
     * @param currentPackIndex  当前出块下标
     */
    private static void switchVoteRound(Chain chain, long currentRoundIndex, int currentPackIndex, byte currentVoteRound, long currentVoteTime) {
        try {
            switchVoteInfoLock.lock();
            boolean isSwitch = currentRoundIndex > CURRENT_BLOCK_VOTE_DATA.getRoundIndex()
                    || (currentRoundIndex == CURRENT_BLOCK_VOTE_DATA.getRoundIndex() && currentPackIndex >= CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound());
            if (!isSwitch) {
                chain.getLogger().warn("Current round switched");
                return;
            }
            byte nextVoteRound = (byte) (currentVoteRound + 1);
            long nextVoteTime = currentVoteTime + ConsensusConstant.VOTE_ROUND_INTERVAL_TIME;
            //判断是否已收到过下一轮的投票信息,如果已存在则判断投票时间是否正确
            VoteRoundData nextVoteRoundData = CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().get(nextVoteRound);
            if (nextVoteRoundData != null) {
                if (nextVoteRoundData.getTime() != nextVoteTime) {
                    chain.getLogger().warn("The time of the next round of voting information currently cached is wrong");
                    CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().remove(nextVoteRound);
                }
            }
            //切换当前投票消息队列数据
            VoteCache.switchVoteRoundMessage(nextVoteRound, nextVoteTime);
            PRE_ROUND_CONFIRMED = true;
        } finally {
            switchVoteInfoLock.unlock();
        }
    }

    /**
     * 判断投票结果数据时间点
     *
     * @param roundIndex 轮次
     * @param packIndex  打包下标
     * @param voteRound  投票伦次
     */
    private static byte resultTime(long roundIndex, int packIndex, byte voteRound) {
        boolean isPrevious = roundIndex < CURRENT_BLOCK_VOTE_DATA.getRoundIndex()
                || (roundIndex == CURRENT_BLOCK_VOTE_DATA.getRoundIndex() && packIndex < CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound())
                || (roundIndex == CURRENT_BLOCK_VOTE_DATA.getRoundIndex() && packIndex == CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound() && voteRound < CURRENT_BLOCK_VOTE_DATA.getRoundIndex());
        if (isPrevious) {
            return ConsensusConstant.PREVIOUS_ROUND;
        }
        boolean isCurrentBlock = roundIndex == CURRENT_BLOCK_VOTE_DATA.getRoundIndex() && packIndex == CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound();
        boolean isCurrentRound = isCurrentBlock && voteRound == CURRENT_BLOCK_VOTE_DATA.getRoundIndex();
        if (isCurrentRound) {
            return ConsensusConstant.CURRENT_ROUND;
        }
        if (isCurrentBlock) {
            return ConsensusConstant.CURRENT_BLOCK;
        }
        return ConsensusConstant.FUTURE;
    }

    /**
     * 通知区块模块区块拜占庭完成
     *
     * @param voteResultItem 拜占庭结果信息
     */
    private static void noticeByzantineResult(Chain chain, VoteResultItem voteResultItem) {
        boolean bifurcate = voteResultItem.getFirstHeader() != null && voteResultItem.getSecondHeader() != null;
        NulsHash firstHash = bifurcate ? voteResultItem.getFirstHeader().getHash() : voteResultItem.getBlockHash();
        NulsHash secondHash = bifurcate ? voteResultItem.getSecondHeader().getHash() : null;
        CallMethodUtils.noticeByzantineResult(chain, voteResultItem.getHeight(), bifurcate, firstHash, secondHash);
    }
}
