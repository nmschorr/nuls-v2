package io.nuls.pocbft.utils.manager;
import io.nuls.base.data.NulsHash;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.tx.txdata.Deposit;
import io.nuls.pocbft.model.po.DepositPo;
import io.nuls.pocbft.storage.DepositStorageService;
import io.nuls.pocbft.utils.compare.DepositComparator;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 委托信息管理类，负责委托信息相关处理
 * Delegated information management category, responsible for delegated information related processing
 *
 * @author tag
 * 2018/12/5
 */
@Component
public class DepositManager {
    @Autowired
    private DepositStorageService depositStorageService;

    /**
     * 初始化委托信息
     * Initialize delegation information
     *
     * @param chain 链信息/chain info
     */
    public void loadDeposits(Chain chain) throws Exception {
        List<Deposit> allDepositList = new ArrayList<>();
        List<DepositPo> poList = depositStorageService.getList(chain.getConfig().getChainId());
        for (DepositPo po : poList) {
            Deposit deposit = new Deposit(po);
            allDepositList.add(deposit);
        }
        allDepositList.sort(new DepositComparator());
        chain.setDepositList(allDepositList);
    }

    /**
     * 添加委托缓存
     * Add delegation cache
     *
     * @param chain   chain info
     * @param deposit deposit info
     */
    public boolean addDeposit(Chain chain, Deposit deposit) {
        if (!depositStorageService.save(new DepositPo(deposit), chain.getConfig().getChainId())) {
            chain.getLogger().error("Data save error!");
            return false;
        }
        chain.getDepositList().add(deposit);
        return true;
    }

    /**
     * 修改委托缓存
     * modify delegation cache
     *
     * @param chain   chain
     * @param deposit deposit info
     */
    public boolean updateDeposit(Chain chain, Deposit deposit) {
        if (!depositStorageService.save(new DepositPo(deposit), chain.getChainId())) {
            chain.getLogger().error("Data save error!");
            return false;
        }
        for (Deposit oldDeposit:chain.getDepositList()) {
            if(oldDeposit.getTxHash().equals(deposit.getTxHash())){
                oldDeposit.setDelHeight(deposit.getDelHeight());
                break;
            }
        }
        return true;
    }

    /**
     * 删除指定链的委托信息
     * Delete delegate information for a specified chain
     *
     * @param chain  chain nfo
     * @param txHash 创建该委托交易的Hash/Hash to create the delegated transaction
     */
    public boolean removeDeposit(Chain chain, NulsHash txHash){
        if (!depositStorageService.delete(txHash, chain.getConfig().getChainId())) {
            chain.getLogger().error("Data save error!");
            return false;
        }
        chain.getDepositList().removeIf(s -> s.getTxHash().equals(txHash));
        return true;
    }

    /**
     * 获取指定委托信息
     * Get the specified delegation information
     *
     * @param chain  chain nfo
     * @param txHash 创建该委托交易的Hash/Hash to create the delegated transaction
     */
    public Deposit getDeposit(Chain chain, NulsHash txHash){
        for (Deposit deposit:chain.getDepositList()) {
            if(deposit.getTxHash().equals(txHash)){
                return deposit;
            }
        }
        return null;
    }
}
