import java.util.ArrayList;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double inputSum = 0;
        double outputSum = 0;
        ArrayList<UTXO> claimedUTXOs = new ArrayList<>();

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            int outputIndex = input.outputIndex;
            byte[] prevTxHash = input.prevTxHash;
            byte[] signature = input.signature;

            //Creates a new UTXO corresponding to the outputIndex in the transaction whose hash is prevTxHash.
            UTXO utxo = new UTXO(prevTxHash, outputIndex);

            //1: Checks if all outputs claimed by tx are in current UTXO pool.
            if (!utxoPool.contains(utxo)) {
                return false;
            }

            //2: Checks if the signatures on each input of tx are valid
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            byte[] message = tx.getRawDataToSign(i);
            if (!Crypto.verifySignature(output.address,message,signature)) {
                return false;
            }

            //3: Checks if no UTXO is claimed multiple times by tx
            if (claimedUTXOs.contains(utxo)) {
                return false;
            }
            claimedUTXOs.add(utxo);

            inputSum += output.value;
        }

        for (int i=0;i<tx.numOutputs();i++) {
            //4: Check if all of tx output values are non-negative
            Transaction.Output output = tx.getOutput(i);
            if (output.value < 0) {
                return false;
            }

            outputSum += output.value;
        }

        //5: Checks if the sum of tx input values is greater than or equal to the sum of its output values
        if (inputSum < outputSum) {
            return false;
        }

        //If this is reached it means that the tx is valid.
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> validTxs = new ArrayList<>();

        for (Transaction t : possibleTxs) {
            if (isValidTx(t)) {
                validTxs.add(t);

                //Remove UTXO
                for (Transaction.Input input : t.getInputs()) {
                    int outputIndex = input.outputIndex;
                    byte[] prevTxHash = input.prevTxHash;
                    UTXO utxo = new UTXO(prevTxHash, outputIndex);
                    utxoPool.removeUTXO(utxo);
                }

                //Add UTXO
                byte[] hash = t.getHash();
                for (int i = 0; i < t.numOutputs(); i++) {
                    UTXO utxo = new UTXO(hash, i);
                    utxoPool.addUTXO(utxo, t.getOutput(i));
                }
            }
        }

        //Create an array of transactions
        Transaction[] validTxsArr = new Transaction[validTxs.size()];
        validTxsArr = validTxs.toArray(validTxsArr);
        return validTxsArr;
    }
}
