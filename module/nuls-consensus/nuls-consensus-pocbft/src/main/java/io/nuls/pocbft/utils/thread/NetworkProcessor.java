package io.nuls.pocbft.utils.thread;

import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.utils.manager.ChainManager;
import io.nuls.pocnetwork.service.ConsensusNetService;

/**
 * 监控网络状态
 */
public class NetworkProcessor implements Runnable {
    private ChainManager chainManager = SpringLiteContext.getBean(ChainManager.class);
    private Chain chain;

    public NetworkProcessor(Chain chain) {
        this.chain = chain;
    }
    @Override
    public void run() {
        while (true) {
            boolean netStatus = false;
            try {
                //如果不是共识节点或则共识网络未组好则直接返回
                ConsensusNetService consensusNetService = SpringLiteContext.getBean(ConsensusNetService.class);

                boolean isChange = consensusNetService.netStatusChange(chain);
                if (isChange) {
                    //通知回调
                    netStatus = consensusNetService.getNetStatus(chain);
                    chainManager.netWorkStateChange(chain, netStatus);
                }
            } catch (Exception e) {
                chain.getLogger().error(e);
            }
            if(netStatus) {
                try {
                    Thread.sleep(15000L);
                } catch (InterruptedException e) {
                    chain.getLogger().error(e);
                }
            }else{
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    chain.getLogger().error(e);
                }
            }
        }
    }
}
