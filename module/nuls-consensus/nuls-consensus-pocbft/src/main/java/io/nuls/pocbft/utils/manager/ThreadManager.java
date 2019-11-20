package io.nuls.pocbft.utils.manager;

import io.nuls.pocbft.model.bo.Chain;
import io.nuls.core.core.annotation.Component;
import io.nuls.pocbft.utils.thread.*;

/**
 * 共识模块任务管理器
 * Consensus Module Task Manager
 *
 * @author tag
 * 2018/11/9
 * */
@Component
public class ThreadManager {
    /**
     * 创建一条链的任务
     * The task of creating a chain
     *
     * @param chain chain info
     * */
    public void createChainThread(Chain chain){
        /*
        创建链相关的任务
        Chain-related tasks
        */
        chain.getThreadPool().execute(new VoteProcessor(chain));
        chain.getThreadPool().execute(new StageOneVoteCollector(chain));
        chain.getThreadPool().execute(new StageTwoVoteCollector(chain));
        chain.getThreadPool().execute(new VoteResultProcessor(chain));
    }
    public void createConsensusNetThread(Chain chain){
        chain.getThreadPool().execute(new NetworkProcessor(chain));
    }
}
