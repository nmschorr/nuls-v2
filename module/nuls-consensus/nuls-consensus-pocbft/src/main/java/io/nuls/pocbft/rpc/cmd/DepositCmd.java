package io.nuls.pocbft.rpc.cmd;

import io.nuls.core.rpc.model.*;
import io.nuls.pocbft.model.dto.output.DepositDTO;
import io.nuls.pocbft.service.DepositService;
import io.nuls.core.rpc.cmd.BaseCmd;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.core.basic.Result;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import static io.nuls.pocbft.constant.CommandConstant.*;
import static io.nuls.pocbft.constant.ParameterConstant.*;

import java.util.List;
import java.util.Map;

/**
 * 共识委托相关接口
 * @author tag
 * 2018/11/7
 * */
@Component
public class DepositCmd extends BaseCmd {
    @Autowired
    private DepositService service;

    /**
     * 委托共识
     * */
    @CmdAnnotation(cmd = CMD_DEPOSIT_TO_AGENT, version = 1.0, description = "创建委托交易/deposit agent transaction")
    @Parameter(parameterName = PARAM_CHAIN_ID, requestType = @TypeDescriptor(value = int.class), parameterDes = "链id")
    @Parameter(parameterName = PARAM_ADDRESS, parameterType = "String", parameterDes = "账户地址")
    @Parameter(parameterName = PARAM_AGENT_HASH, parameterType = "String", parameterDes = "节点HASH")
    @Parameter(parameterName = PARAM_DEPOSIT, parameterType = "String", parameterDes = "委托金额")
    @Parameter(parameterName = PARAM_PASSWORD, parameterType = "String", parameterDes = "账户密码")
    @ResponseData(name = "返回值", description = "加入共识交易Hash", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = PARAM_TX_HASH, description = "加入共识交易Hash")
    }))
    public Response depositToAgent(Map<String,Object> params){
        Result result = service.depositToAgent(params);
        if(result.isFailed()){
            return failed(result.getErrorCode());
        }
        return success(result.getData());
    }

    /**
     * 退出共识
     * */
    @CmdAnnotation(cmd = CMD_WITHDRAW, version = 1.0, description = "退出委托交易/withdraw deposit agent transaction")
    @Parameter(parameterName = PARAM_CHAIN_ID, requestType = @TypeDescriptor(value = int.class), parameterDes = "链id")
    @Parameter(parameterName = PARAM_ADDRESS, parameterType = "String", parameterDes = "账户地址")
    @Parameter(parameterName = PARAM_TX_HASH, parameterType = "String", parameterDes = "加入共识交易HASH")
    @Parameter(parameterName = PARAM_PASSWORD, parameterType = "String", parameterDes = "账户密码")
    @ResponseData(name = "返回值", description = "退出共识交易Hash", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = PARAM_TX_HASH, description = "退出共识交易Hash")
    }))
    public Response withdraw(Map<String,Object> params){
        Result result = service.withdraw(params);
        if(result.isFailed()){
            return failed(result.getErrorCode());
        }
        return success(result.getData());
    }

    /**
     * 查询委托信息列表
     * */
    @CmdAnnotation(cmd = CMD_GET_DEPOSIT_LIST, version = 1.0, description = "查询指定账户或指定节点的委托信息/Query delegation information for a specified account or node")
    @Parameter(parameterName = PARAM_CHAIN_ID, requestType = @TypeDescriptor(value = int.class), parameterDes = "链id")
    @Parameter(parameterName = PARAM_PAGE_NUMBER, requestType = @TypeDescriptor(value = int.class), parameterDes = "页码")
    @Parameter(parameterName = PARAM_PAGE_SIZE, requestType = @TypeDescriptor(value = int.class), parameterDes = "每页数量")
    @Parameter(parameterName = PARAM_ADDRESS, parameterType = "String", parameterDes = "账户地址")
    @ResponseData(name = "返回值", description = "返回一个Page对象，这里只描述Page对象中的集合",
            responseType = @TypeDescriptor(value = List.class, collectionElement = DepositDTO.class)
    )
    public Response getDepositList(Map<String,Object> params){
        Result result = service.getDepositList(params);
        if(result.isFailed()){
            return failed(result.getErrorCode());
        }
        return success(result.getData());
    }
}
