package io.nuls.pocbft.utils.manager;

import io.nuls.base.protocol.ProtocolGroupManager;
import io.nuls.base.protocol.ProtocolLoader;
import io.nuls.base.protocol.RegisterHelper;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.log.Log;
import io.nuls.core.rockdb.constant.DBErrorCode;
import io.nuls.core.rockdb.service.RocksDBService;
import io.nuls.economic.base.service.EconomicService;
import io.nuls.economic.nuls.constant.ParamConstant;
import io.nuls.economic.nuls.model.bo.ConsensusConfigInfo;
import io.nuls.pocbft.model.bo.config.ChainConfig;
import io.nuls.pocbft.model.bo.config.ConsensusChainConfig;
import io.nuls.pocbft.constant.ConsensusConstant;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.rpc.call.CallMethodUtils;
import io.nuls.pocbft.storage.ConfigService;
import io.nuls.pocbft.utils.LoggerUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链管理类,负责各条链的初始化,运行,启动,参数维护等
 * Chain management class, responsible for the initialization, operation, start-up, parameter maintenance of each chain, etc.
 *
 * @author tag
 * 2018/12/4
 */
@Component
public class ChainManager {
    @Autowired
    private ConfigService configService;
    @Autowired
    private AgentManager agentManager;
    @Autowired
    private DepositManager depositManager;
    @Autowired
    private PunishManager punishManager;
    @Autowired
    private RoundManager roundManager;
    @Autowired
    private ThreadManager threadManager;
    @Autowired
    private ConsensusChainConfig config;
    @Autowired
    private EconomicService economicService;
    @Autowired
    private AgentDepositManager agentDepositManager;

    private final Map<Integer, Chain> chainMap = new ConcurrentHashMap<>();

    /**
     * 初始化
     * Initialization chain
     * */
    public void initChain() throws Exception {
        Map<Integer, ChainConfig> configMap = configChain();
        if (configMap == null || configMap.size() == 0) {
            Log.info("链初始化失败！");
            return;
        }
        for (Map.Entry<Integer, ChainConfig> entry : configMap.entrySet()){
            Chain chain = new Chain();
            int chainId = entry.getKey();
            ChainConfig chainConfig = entry.getValue();
            chain.setConfig(chainConfig);
            chain.setSeedNodeList(List.of(chainConfig.getSeedNodes().split(ConsensusConstant.SEED_NODE_SEPARATOR)));
            /*
             * 初始化链日志对象
             * Initialization Chain Log Objects
             * */
            initLogger(chain);
            /*
            初始化链数据库表
            Initialize linked database tables
            */
            initTable(chain);
            chainMap.put(chainId, chain);
            ProtocolLoader.load(chainId);
            Map<String,Object> param = new HashMap<>(4);
            param.put(ParamConstant.CONSENUS_CONFIG, new ConsensusConfigInfo(chainId, chainConfig.getAssetId(), chainConfig.getPackingInterval(),
                    chainConfig.getInflationAmount(), chainConfig.getTotalInflationAmount(), chainConfig.getInitTime(), chainConfig.getDeflationRatio(), chainConfig.getDeflationTimeInterval(), chainConfig.getAwardAssetId()));
            economicService.registerConfig(param);
        }
    }

    /**
     * 注册链交易
     * Registration Chain Transaction
     * */
    public void registerTx(){
        for (Chain chain:chainMap.values()) {
            /*
             * 链交易注册
             * Chain Trading Registration
             * */
            int chainId = chain.getConfig().getChainId();
            RegisterHelper.registerTx(chainId, ProtocolGroupManager.getCurrentProtocol(chainId));
        }
    }

    /**
     * 加载链缓存数据并启动链
     * Load the chain to cache data and start the chain
     * */
    public void runChain(){
        for (Chain chain:chainMap.values()) {
            /*
            加载链缓存数据
            Load chain caching entity
            */
            initCache(chain);

            /*
            创建并启动链内任务
            Create and start in-chain tasks
            */
            threadManager.createChainThread(chain);
        }
    }


    /**
     * 读取配置文件创建并初始化链
     * Read the configuration file to create and initialize the chain
     */
    private Map<Integer, ChainConfig> configChain() {
        try {
            /*
            读取数据库链信息配置
            Read database chain information configuration
             */
            Map<Integer, ChainConfig> configMap = configService.getList();
            /*
            如果系统是第一次运行，则本地数据库没有存储链信息，此时需要从配置文件读取主链配置信息
            If the system is running for the first time, the local database does not have chain information,
            and the main chain configuration information needs to be read from the configuration file at this time.
            */
            if(configMap == null){
                configMap = new HashMap<>(ConsensusConstant.INIT_CAPACITY_2);
            }
            if (configMap.size() == 0) {
                ChainConfig chainConfig = config;
                boolean saveSuccess = configService.save(chainConfig, chainConfig.getChainId());
                if(saveSuccess){
                    configMap.put(chainConfig.getChainId(), chainConfig);
                }
            }
            return configMap;
        } catch (Exception e) {
            Log.error(e);
            return null;
        }
    }

    /**
     * 初始化链相关表
     * Initialization chain correlation table
     *
     * @param chain chain info
     */
    private void initTable(Chain chain) {
        String dbNameSuffix = ConsensusConstant.SEPARATOR + chain.getConfig().getChainId();
        try {
            /*
            创建共识节点表
            Create consensus node tables
            */
            RocksDBService.createTable(ConsensusConstant.DB_NAME_AGENT + dbNameSuffix);

            /*
            追加保证金
            Additional margin
            */
            RocksDBService.createTable(ConsensusConstant.DB_NAME_APPEND_DEPOSIT + dbNameSuffix);

            /*
            减少保证金
            Additional margin
            */
            RocksDBService.createTable(ConsensusConstant.DB_NAME_REDUCE_DEPOSIT + dbNameSuffix);

            /*
            委托信息表
            Create consensus information tables
            */
            RocksDBService.createTable(ConsensusConstant.DB_NAME_DEPOSIT + dbNameSuffix);

            /*
            创建红黄牌信息表
            Creating Red and Yellow Card Information Table
            */
            RocksDBService.createTable(ConsensusConstant.DB_NAME_PUNISH + dbNameSuffix);
            /*
            创建底层随机数表
            */
            RocksDBService.createTable(ConsensusConstant.DB_NAME_RANDOM_SEEDS + dbNameSuffix);
        } catch (Exception e) {
            if (!DBErrorCode.DB_TABLE_EXIST.equals(e.getMessage())) {
                chain.getLogger().error(e.getMessage());
            } else {
                chain.getLogger().error(e.getMessage());
            }
        }
    }

    private void initLogger(Chain chain) {
        /*
         * 共识模块日志文件对象创建,如果一条链有多类日志文件，可在此添加
         * Creation of Log File Object in Consensus Module，If there are multiple log files in a chain, you can add them here
         * */
        LoggerUtil.initLogger(chain);
    }

    /**
     * 初始化链缓存数据
     * 在poc的共识机制下，由于存在轮次信息，节点信息，以及节点被惩罚的红黄牌信息，
     * 因此需要在初始化的时候，缓存相关的数据，用于计算最新的轮次信息，以及各个节点的信用值等
     * Initialize chain caching entity
     *
     * @param chain chain info
     */
    private void initCache(Chain chain) {
        try {
            CallMethodUtils.loadBlockHeader(chain);
            agentManager.loadAgents(chain);
            depositManager.loadDeposits(chain);
            punishManager.loadPunishes(chain);
            agentDepositManager.loadAppendDeposits(chain);
            agentDepositManager.loadReduceDeposits(chain);
        } catch (Exception e) {
            chain.getLogger().error(e);
        }
    }

    public Map<Integer, Chain> getChainMap() {
        return chainMap;
    }
}
