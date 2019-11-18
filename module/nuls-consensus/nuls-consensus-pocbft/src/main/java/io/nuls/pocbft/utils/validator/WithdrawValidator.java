package io.nuls.pocbft.utils.validator;

import io.nuls.base.data.CoinData;
import io.nuls.base.data.Transaction;
import io.nuls.core.basic.Result;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.exception.NulsException;
import io.nuls.pocbft.constant.ConsensusErrorCode;
import io.nuls.pocbft.model.bo.Chain;
import io.nuls.pocbft.model.bo.tx.txdata.CancelDeposit;
import io.nuls.pocbft.model.po.DepositPo;
import io.nuls.pocbft.storage.DepositStorageService;
import io.nuls.pocbft.utils.validator.base.BaseValidator;

import java.io.IOException;
import java.util.Arrays;

import static io.nuls.pocbft.utils.TxUtil.getSuccess;

/**
 * 减少保证金交易验证器
 * @author  tag
 * */
@Component
public class WithdrawValidator extends BaseValidator {
    @Autowired
    private DepositStorageService depositStorageService;
    @Override
    public Result validate(Chain chain, Transaction tx) throws NulsException, IOException {
        CancelDeposit txData = new CancelDeposit();
        txData.parse(tx.getTxData(), 0);
        DepositPo depositPo = depositStorageService.get(txData.getJoinTxHash(), chain.getConfig().getChainId());
        if (depositPo == null || depositPo.getDelHeight() > 0) {
            chain.getLogger().error("Withdraw -- Deposit transaction does not exist");
            return Result.getFailed(ConsensusErrorCode.DATA_NOT_EXIST);
        }

        //验证退出账户与委托账户是否一样
        if(!Arrays.equals(txData.getAddress(), depositPo.getAddress())){
            chain.getLogger().error("Withdraw -- The account is inconsistent with the entrusted account");
            return Result.getFailed(ConsensusErrorCode.ACCOUNT_IS_NOT_CREATOR);
        }

        //coinData验证
        CoinData coinData = new CoinData();
        coinData.parse(tx.getTxData(),0);
        Result rs = reduceDepositCoinDataValid(chain, depositPo.getDeposit(), coinData, txData.getAddress(), 0,depositPo.getAssetChainId(),depositPo.getAssetId());
        if(rs.isFailed()){
            return rs;
        }

        //验证手续费
        rs = validFee(chain, coinData, tx);
        if(rs.isFailed()){
            return rs;
        }

        return getSuccess();
    }
}
