package io.nuls.pocbft.service.impl;

import io.nuls.base.RPCUtil;
import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.data.Block;
import io.nuls.base.data.BlockExtendsData;
import io.nuls.base.data.BlockHeader;
import io.nuls.core.basic.Result;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.exception.NulsException;
import io.nuls.core.parse.JSONUtils;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.pocbft.constant.CommandConstant;
import io.nuls.pocbft.constant.ConsensusErrorCode;
import io.nuls.pocbft.message.GetVoteResultMessage;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.dto.input.ValidBlockDTO;
import io.nuls.pocbft.rpc.call.CallMethodUtils;
import io.nuls.pocbft.rpc.call.NetWorkCall;
import io.nuls.pocbft.service.BlockService;
import io.nuls.pocbft.utils.manager.BlockManager;
import io.nuls.pocbft.utils.manager.ChainManager;
import io.nuls.pocbft.utils.validator.BlockValidator;
import static io.nuls.pocbft.constant.ParameterConstant.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共识模块RPC接口实现类
 * Consensus Module RPC Interface Implementation Class
 *
 * @author tag
 * 2018/11/7
 */
@Component
public class BlockServiceImpl implements BlockService {

    @Autowired
    private ChainManager chainManager;

    @Autowired
    private BlockManager blockManager;

    @Autowired
    private BlockValidator blockValidator;
    /**
     * 缓存最新区块
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result addBlock(Map<String, Object> params) {
        if (params.get(PARAM_CHAIN_ID) == null || params.get(PARAM_BLOCK_HEADER) == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        int chainId = (Integer) params.get(PARAM_CHAIN_ID);
        if (chainId <= MIN_VALUE) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        try {
            String headerHex = (String) params.get(PARAM_BLOCK_HEADER);
            BlockHeader header = new BlockHeader();
            header.parse(RPCUtil.decode(headerHex), 0);
            blockManager.addNewBlock(chain, header);
            Map<String, Object> validResult = new HashMap<>(2);
            validResult.put(PARAM_RESULT_VALUE, true);
            return Result.getSuccess(ConsensusErrorCode.SUCCESS).setData(validResult);
        } catch (NulsException e) {
            chain.getLogger().error(e);
            return Result.getFailed(e.getErrorCode());
        }
    }

    /**
     * 链分叉区块回滚
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result chainRollBack(Map<String, Object> params) {
        if (params.get(PARAM_CHAIN_ID) == null || params.get(PARAM_HEIGHT) == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        int chainId = (Integer) params.get(PARAM_CHAIN_ID);
        if (chainId <= MIN_VALUE) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        int height = (Integer) params.get(PARAM_HEIGHT);
        blockManager.chainRollBack(chain, height);
        Map<String, Object> validResult = new HashMap<>(2);
        validResult.put(PARAM_RESULT_VALUE, true);
        return Result.getSuccess(ConsensusErrorCode.SUCCESS).setData(validResult);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result receiveHeaderList(Map<String, Object> params) {
        if (params.get(PARAM_CHAIN_ID) == null || params.get(PARAM_HEADER_LIST) == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        int chainId = (Integer) params.get(PARAM_CHAIN_ID);
        if (chainId <= MIN_VALUE) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        try {
            List<String> headerList = (List<String>) params.get(PARAM_HEADER_LIST);
            List<BlockHeader> blockHeaderList = new ArrayList<>();
            for (String header:headerList) {
                BlockHeader blockHeader = new BlockHeader();
                blockHeader.parse(RPCUtil.decode(header),0);
                blockHeaderList.add(blockHeader);
            }
            List<BlockHeader> localBlockHeaders = chain.getBlockHeaderList();
            localBlockHeaders.addAll(0, blockHeaderList);
            Map<String, Object> validResult = new HashMap<>(2);
            validResult.put(PARAM_RESULT_VALUE, true);
            return Result.getSuccess(ConsensusErrorCode.SUCCESS).setData(validResult);
        } catch (NulsException e) {
            chain.getLogger().error(e);
            return Result.getFailed(e.getErrorCode());
        }
    }

    /**
     * 验证区块正确性
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result validBlock(Map<String, Object> params) {
        if (params == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }
        ValidBlockDTO dto = JSONUtils.map2pojo(params, ValidBlockDTO.class);
        if (dto.getChainId() <= MIN_VALUE || dto.getBlock() == null) {
            return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
        }

        int chainId = dto.getChainId();
        Chain chain = chainManager.getChainMap().get(chainId);
        if (chain == null) {
            return Result.getFailed(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        /*
         * 0区块下载中，1接收到最新区块
         * */
        boolean isDownload = (dto.getDownload() == 0);
        String blockHex = dto.getBlock();
        Map<String, Object> validResult = new HashMap<>(2);
        validResult.put(PARAM_RESULT_VALUE, false);
        Block block = new Block();
        try {
            block.parse(new NulsByteBuffer(RPCUtil.decode(blockHex)));
        }catch (NulsException e){
            chain.getLogger().error(e);
            return Result.getFailed(e.getErrorCode()).setData(validResult);
        }
        if(dto.isBasicVerify()){
            try {
                blockValidator.validate(chain, block);
                Response response = CallMethodUtils.verify(chainId, block.getTxs(), block.getHeader(), chain.getNewestHeader(), chain.getLogger());
                if (response != null && response.isSuccess()) {
                    Map responseData = (Map) response.getResponseData();
                    Map v = (Map) responseData.get(PARAM_TX_BATCH_VERIFY);
                    return Result.getSuccess(ConsensusErrorCode.SUCCESS).setData(v);
                }else{
                    chain.getLogger().info("Block transaction validation failed!");
                }
            } catch (NulsException e) {
                chain.getLogger().error(e);
                return Result.getFailed(e.getErrorCode()).setData(validResult);
            } catch (IOException e) {
                chain.getLogger().error(e);
            }
        }
        if(dto.isByzantineVerify()){
            if(dto.getNodeId() == null || dto.getNodeId().isEmpty()){
                return Result.getFailed(ConsensusErrorCode.PARAM_ERROR);
            }
            BlockHeader blockHeader = block.getHeader();
            BlockExtendsData blockExtendsData = blockHeader.getExtendsData();
            //区块拜占庭验证
            GetVoteResultMessage getVoteResultMessage = new GetVoteResultMessage(blockHeader.getHeight(),blockExtendsData.getRoundIndex(),blockExtendsData.getPackingIndexOfRound(),(byte)0);
            NetWorkCall.sendToNode(chainId, getVoteResultMessage, dto.getNodeId(), CommandConstant.MESSAGE_GET_VOTE_RESULT);
        }
        return Result.getFailed(ConsensusErrorCode.FAILED).setData(validResult);
    }
}
