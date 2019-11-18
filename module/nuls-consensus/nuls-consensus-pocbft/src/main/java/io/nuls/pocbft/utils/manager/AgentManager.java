package io.nuls.pocbft.utils.manager;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.NulsHash;
import io.nuls.core.basic.Result;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.constant.ConsensusErrorCode;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.round.MeetingMember;
import io.nuls.pocbft.model.bo.round.MeetingRound;
import io.nuls.pocbft.model.bo.tx.txdata.Agent;
import io.nuls.pocbft.model.po.AgentPo;
import io.nuls.pocbft.storage.AgentStorageService;
import io.nuls.pocbft.utils.compare.AgentComparator;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.pocbft.utils.compare.AgentDepositComparator;

import java.math.BigInteger;
import java.util.*;

import static io.nuls.pocbft.utils.TxUtil.getSuccess;

/**
 * 节点管理类，负责节点的相关操作
 * Node management class, responsible for the operation of the node
 *
 * @author tag
 * 2018/12/5
 */
@Component
public class AgentManager {
    @Autowired
    private AgentStorageService agentStorageService;
    @Autowired
    private RoundManager roundManager;
    @Autowired
    private AgentDepositManager agentDepositManager;

    /**
     * 加载节点信息
     * Initialize node information
     *
     * @param chain 链信息/chain info
     */
    public void loadAgents(Chain chain) throws Exception {
        List<Agent> allAgentList = new ArrayList<>();
        List<AgentPo> poList = this.agentStorageService.getList(chain.getConfig().getChainId());
        for (AgentPo po : poList) {
            Agent agent = new Agent(po);
            allAgentList.add(agent);
        }
        allAgentList.sort(new AgentComparator());
        chain.setAgentList(allAgentList);
    }

    /**
     * 添加指定链节点
     * Adding specified chain nodes
     *
     * @param chain chain info
     * @param agent agent info
     */
    public boolean addAgent(Chain chain, Agent agent) {
        if (!agentStorageService.save(new AgentPo(agent), chain.getConfig().getChainId())) {
            chain.getLogger().error("Agent data save error!");
            return false;
        }
        chain.getAgentList().add(agent);
        return true;
    }

    /**
     * 修改指定链节点
     * Modifying specified chain nodes
     *
     * @param chain          chain info
     * @param realAgent      agent info
     */
    public boolean updateAgent(Chain chain, Agent realAgent) {
        if(!agentStorageService.save(new AgentPo(realAgent), chain.getChainId())){
            chain.getLogger().error("Agent data update error!");
            return false;
        }

        for (Agent agent : chain.getAgentList()) {
            if(agent.getTxHash().equals(realAgent.getTxHash())){
                agent.setDelHeight(realAgent.getDelHeight());
                agent.setDeposit(realAgent.getDeposit());
                break;
            }
        }

        return true;
    }

    /**
     * 删除指定链节点
     * Delete the specified link node
     *
     * @param chain  chain info
     * @param txHash 创建该节点交易的HASH/Creating the node transaction hash
     */
    public boolean removeAgent(Chain chain, NulsHash txHash) {
        if (!agentStorageService.delete(txHash, chain.getConfig().getChainId())) {
            chain.getLogger().error("Data save error!");
            return false;
        }
        chain.getAgentList().removeIf(s -> s.getTxHash().equals(txHash));
        return true;
    }

    /**
     * 查询指定节点
     * Query specified nodes
     *
     * @param chain  chain info
     * @param txHash 创建该节点交易的HASH/Creating the node transaction hash
     */
    public Agent getAgentByHash(Chain chain, NulsHash txHash){
        for (Agent agent : chain.getAgentList()) {
            if (txHash.equals(agent.getTxHash())) {
                return agent;
            }
        }
        return null;
    }

    /**
     * 查询指定节点
     * Query specified nodes
     *
     * @param chain          chain info
     * @param agentAddress   节点地址
     */
    public Agent getAgentByAddress(Chain chain, byte[] agentAddress){
        for (Agent agent:chain.getAgentList()) {
            if(Arrays.equals(agentAddress, agent.getAgentAddress())){
                return agent;
            }
        }
        return null;
    }

    /**
     * 获取节点id
     * Get agent id
     *
     * @param hash 节点HASH/Agent hash
     * @return String
     */
    public String getAgentId(NulsHash hash) {
        String hashHex = hash.toHex();
        return hashHex.substring(hashHex.length() - 8).toUpperCase();
    }

    public void fillAgentList(Chain chain, List<Agent> agentList) {
        MeetingRound round = roundManager.getCurrentRound(chain);
        for (Agent agent : agentList) {
            fillAgent(agent, round);
        }
    }

    public void fillAgent(Agent agent, MeetingRound round) {
        if (round == null) {
            return;
        }
        MeetingMember member = round.getOnlyMember(agent.getPackingAddress());
        if (null == member) {
            agent.setStatus(0);
            return;
        } else {
            agent.setStatus(1);
        }
        agent.setCreditVal(member.getAgent().getRealCreditVal());
    }

    /**
     * 验证节点是否存在且指定地址是否为创建者
     * Verify that the node exists
     *
     * @param chain       链信息
     * @param agentHash   节点Hash
     * @param address     创建者地址
     * @return            创建者是否正确
     * */
    @SuppressWarnings("unchecked")
    public Result creatorValid(Chain chain , NulsHash agentHash, byte[] address){
        AgentPo agentPo = agentStorageService.get(agentHash, chain.getChainId());
        if (agentPo == null || agentPo.getDelHeight() > 0) {
            chain.getLogger().error("Deposit -- Agent does not exist");
            return Result.getFailed(ConsensusErrorCode.AGENT_NOT_EXIST);
        }
        if(!Arrays.equals(agentPo.getAgentAddress(), address)){
            chain.getLogger().error("AddAgentDeposit -- Account is not the agent Creator");
            return Result.getFailed(ConsensusErrorCode.ACCOUNT_IS_NOT_CREATOR);
        }
        return getSuccess().setData(agentPo);
    }


    /**
     * 获取当前未注销的所有节点列表(创建节点时避免账户重复创建节点)
     * Get a list of nodes that meet block requirements
     *
     * @param chain        链信息
     * @return List<agent>
     **/
    public List<Agent> getAgentList(Chain chain, long height) {
        List<Agent> agentList = new ArrayList<>();
        for (Agent agent : chain.getAgentList()) {
            if (agent.getDelHeight() != -1L && agent.getDelHeight() <= height) {
                continue;
            }
            if (agent.getBlockHeight() > height || agent.getBlockHeight() < 0L) {
                continue;
            }
            agentList.add(agent);
        }
        return agentList;
    }

    /**
     * 获取指定高度出块节点地址列表
     * Get a list of nodes that meet block requirements
     *
     * @param chain           链信息
     * @param height          区块高度
     * @return List<agent>
     **/
    public Set<String> getPackAddressList(Chain chain, long height) {
        Set<String> packAddressList = new HashSet<>();
        List<Agent> agentList = getPackAgentList(chain, height);
        agentList.forEach(agent ->
            packAddressList.add(AddressTool.getStringAddressByBytes(agent.getPackingAddress()))
        );
        return packAddressList;
    }

    /**
     * 获取指定高度出块节点列表
     * Get the node list of the specified height out of block
     *
     * @param chain        链信息
     * @param height       指定区块高度
     * @return List<agent>
     **/
    public List<Agent> getPackAgentList(Chain chain, long height) {
        List<Agent> packAgentList = new ArrayList<>();
        boolean isLatestHeight = height == chain.getNewestHeader().getHeight();
        List<Agent> agentList = chain.getAgentList();
        if(!isLatestHeight){
            agentList = getBeforeAgentList(chain, height);
        }
        agentList.sort(new AgentDepositComparator());
        for (Agent agent : agentList) {
            if(agent.getDeposit().compareTo(chain.getConfig().getDepositMin()) >= 0){
                packAgentList.add(agent);
            }else{
                break;
            }
            if(packAgentList.size() >= chain.getConsensusAgentCountMax()){
                break;
            }
        }
        return packAgentList;
    }

    /**
     * 获取当前区块之前的某个高度出块节点列表
     * Get a list of out of block nodes before the current block
     *
     * @param chain        链信息
     * @param height       指定区块高度
     * @return List<agent>
     * */
    private List<Agent> getBeforeAgentList(Chain chain, long height){
        Map<NulsHash, Agent> agentMap = new HashMap<>(ConsensusConstant.INIT_CAPACITY_16);
        for (Agent agent : chain.getAgentList()) {
            if (agent.getDelHeight() != -1L && agent.getDelHeight() <= height) {
                continue;
            }
            if (agent.getBlockHeight() > height || agent.getBlockHeight() < 0L) {
                continue;
            }
            try {
                agentMap.put(agent.getTxHash(), agent.clone());
            }catch (CloneNotSupportedException e){
                chain.getLogger().error(e);
            }
        }
        //获取该高度之后的退出保证金交易
        Map<NulsHash, BigInteger> reduceDepositMap = agentDepositManager.getReduceDepositAfterHeight(chain, height);
        BigInteger realDeposit;
        if(!reduceDepositMap.isEmpty()){
            for (Map.Entry<NulsHash, BigInteger> entry : reduceDepositMap.entrySet()) {
                NulsHash agentHash = entry.getKey();
                if(agentMap.containsKey(agentHash)){
                    realDeposit = agentMap.get(agentHash).getDeposit().add(entry.getValue());
                    agentMap.get(agentHash).setDeposit(realDeposit);
                }
            }
        }
        //获取该高度之后的追加保证金交易
        Map<NulsHash, BigInteger> appendDepositMap = agentDepositManager.getAppendDepositAfterHeight(chain, height);
        if(!appendDepositMap.isEmpty()){
            for (Map.Entry<NulsHash, BigInteger> entry : appendDepositMap.entrySet()) {
                NulsHash agentHash = entry.getKey();
                if(agentMap.containsKey(agentHash)){
                    realDeposit = agentMap.get(agentHash).getDeposit().subtract(entry.getValue());
                    agentMap.get(agentHash).setDeposit(realDeposit);
                }
            }
        }
        return  (List<Agent>) agentMap.values();
    }
}
