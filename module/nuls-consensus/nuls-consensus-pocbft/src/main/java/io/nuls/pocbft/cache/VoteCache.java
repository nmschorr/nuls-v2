package io.nuls.pocbft.cache;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.message.VoteMessage;
import io.nuls.pocbft.message.VoteResultMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.vote.VoteResultData;
import io.nuls.pocbft.model.bo.vote.VoteData;
import io.nuls.pocbft.model.bo.vote.VoteRoundData;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
public class VoteCache {
    /**
     * 当前确认区块投票信息
     * */
    public static VoteData CURRENT_BLOCK_VOTE_DATA = null;

    /**
     * 上一轮投票是否确认完成，用于控制投票线程的执行
     * */
    public static boolean PRE_ROUND_CONFIRMED = true;

    /**
     * 接收的当前投票轮次第一阶段消息队列
     * */
    public static final LinkedBlockingQueue<VoteMessage> CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE = new LinkedBlockingQueue<>();

    /**
     * 接收的当前投票轮次第二阶段消息队列
     * */
    public static final LinkedBlockingQueue<VoteMessage> CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE = new LinkedBlockingQueue<>();

    /**
     * 投票结果消息队列
     * */
    public static final LinkedBlockingQueue<VoteResultMessage> VOTE_RESULT_MESSAGE_QUEUE = new LinkedBlockingQueue<>();

    /**
     * 缓存最近N个已确认的最新区块，确认结果
     * key:roundIndex_packingIndexOfRound
     * value:
     *        key:voteRound（区块最终投票结果该值填0）
     *        value:该轮次确认结果
     * */
    public static final Map<String, Map<Byte, VoteResultData>> CONFIRMED_VOTE_RESULT_MAP = new HashMap<>();

    /**
     * 待处理确认数据
     * key:roundIndex_packingIndexOfRound
     * value:投票数据
     * */
    public static final Map<String, VoteData> FUTURE_VOTE_DATA = new ConcurrentHashMap<>();


    public static void addVoteResult(String currentVoteKey, byte voteRound, VoteResultData voteResultData){
        if (!CONFIRMED_VOTE_RESULT_MAP.containsKey(currentVoteKey)) {
            CONFIRMED_VOTE_RESULT_MAP.put(currentVoteKey, new HashMap<>(ConsensusConstant.INIT_CAPACITY_2));
        }
        CONFIRMED_VOTE_RESULT_MAP.get(currentVoteKey).put(voteRound, voteResultData);
    }

    /**
     * 切换当前投票轮次已收到的消息队列
     * @param nextVoteRound           下一轮信息
     * @param time                下一轮时间
     * */
    public static void switchVoteRoundMessage(byte nextVoteRound, long time){
        CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.clear();
        CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.clear();

        //切换投票轮次信息
        CURRENT_BLOCK_VOTE_DATA.setVoteRound(nextVoteRound);
        CURRENT_BLOCK_VOTE_DATA.setVoteStage(ConsensusConstant.VOTE_STAGE_ONE);

        //已添加到消息队列中的投票
        Map<String,VoteMessage> existStageOneMap = null;
        Map<String,VoteMessage> existStageTwoMap = null;
        if(CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().containsKey(nextVoteRound)){
            existStageOneMap = new ConcurrentHashMap<>(CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_ONE));
            CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.addAll(existStageOneMap.values());
            existStageTwoMap = new ConcurrentHashMap<>(CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_TWO));
            CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.addAll(existStageTwoMap.values());
        }else{
            VoteRoundData voteRoundData = new VoteRoundData(time);
            CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().put(nextVoteRound, voteRoundData);
        }

        //查看切换中途是否收到当前轮次投票信息
        if(existStageOneMap != null && !existStageOneMap.isEmpty()){
            CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.addAll(CURRENT_BLOCK_VOTE_DATA.getMissMessage(existStageOneMap.keySet(), ConsensusConstant.VOTE_STAGE_ONE));
        }else{
            if(!CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_ONE).isEmpty()){
                CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.addAll(CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_ONE).values());
            }
        }
        if(existStageTwoMap != null && existStageTwoMap.isEmpty()){
            CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.addAll(CURRENT_BLOCK_VOTE_DATA.getMissMessage(existStageTwoMap.keySet(), ConsensusConstant.VOTE_STAGE_TWO));
        }else{
            if(!CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_TWO).isEmpty()){
                CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.addAll(CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_TWO).values());
            }
        }
    }

    /**
     * 切换当前投票轮次已收到的消息队列
     * @param nextVoteRound           下一轮投票下标
     * @param time                    下一轮时间
     * @param chain                   链信息
     * @param nextVoteData            下一轮投票数据
     * */
    public static void switchVoteDataMessage(Chain chain, byte nextVoteRound, long time, VoteData nextVoteData){
        long lastRoundIndex = CURRENT_BLOCK_VOTE_DATA.getRoundIndex();
        int lastPackIndex = CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound();
        int agentCount = CURRENT_BLOCK_VOTE_DATA.getAgentCount();
        boolean isLast = lastPackIndex == agentCount;
        CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.clear();
        CURRENT_ROUND_STAGE_TOW_MESSAGE_QUEUE.clear();
        //切换投票数据
        CURRENT_BLOCK_VOTE_DATA = nextVoteData;
        if(CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().containsKey(nextVoteRound)){
            CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.addAll(CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_ONE).values());
            CURRENT_ROUND_STAGE_ONE_MESSAGE_QUEUE.addAll(CURRENT_BLOCK_VOTE_DATA.getStageVoteMessage(ConsensusConstant.VOTE_STAGE_TWO).values());
        }else{
            VoteRoundData voteRoundData = new VoteRoundData(time);
            CURRENT_BLOCK_VOTE_DATA.getVoteRoundMap().put(nextVoteRound, voteRoundData);
        }

        //查看切换中途是否有收到当前区块投票信息
        long currentRoundIndex = CURRENT_BLOCK_VOTE_DATA.getRoundIndex();
        int currentPackIndex = CURRENT_BLOCK_VOTE_DATA.getPackingIndexOfRound();
        boolean isFirst = currentPackIndex == ConsensusConstant.INIT_PACING_INDEX;
        String consensusKey = CURRENT_BLOCK_VOTE_DATA.getConsensusKey();
        VoteData missVoteData = FUTURE_VOTE_DATA.remove(consensusKey);
        if(missVoteData != null){
            for (Map.Entry<Byte,VoteRoundData> entry:missVoteData.getVoteRoundMap().entrySet()) {
                CURRENT_BLOCK_VOTE_DATA.addVoteRoundMessage(chain, entry.getValue(), entry.getKey());
            }
        }
        //切换的投票信息是否连续，如果不连续需要清除两个投票信息之间的缓存数据
        boolean continuousVoteData = (lastRoundIndex == currentRoundIndex && currentPackIndex - lastPackIndex == 1)
                || (currentRoundIndex - lastRoundIndex == 1 && isFirst && isLast);
        if (!continuousVoteData && FUTURE_VOTE_DATA.size() > 0){
           FUTURE_VOTE_DATA.entrySet().removeIf(entry -> (entry.getValue().getRoundIndex() < currentRoundIndex ||
                   (entry.getValue().getRoundIndex() == currentRoundIndex && entry.getValue().getPackingIndexOfRound() < currentPackIndex)));
        }
    }

    /**
     * 添加当前轮次之后的投票消息
     * @param chain                 链信息
     * @param voteMessage           投票信息
     * @param nodeId                发送节点
     * */
    public static void addFutureCache(Chain chain, VoteMessage voteMessage, String nodeId){
        String consensusKey = voteMessage.getConsensusKey();
        VoteData futureVoteData = FUTURE_VOTE_DATA.get(consensusKey);
        if(futureVoteData == null){
            futureVoteData = new VoteData(voteMessage.getRoundIndex(),voteMessage.getPackingIndexOfRound(),CURRENT_BLOCK_VOTE_DATA.getAgentCount(),voteMessage.getHeight(),voteMessage.getRoundStartTime());
        }
        futureVoteData.addVoteMessage(chain, voteMessage, nodeId);
    }
}
