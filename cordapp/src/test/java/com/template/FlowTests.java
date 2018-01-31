package com.template;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.SecureHash;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.identity.Party;
import net.corda.core.node.services.IdentityService;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.node.internal.StartedNode;
import net.corda.testing.node.MockNetwork;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import javax.annotation.Signed;
import java.util.ArrayList;
import java.util.List;

import static net.corda.testing.CoreTestUtils.setCordappPackages;
import static net.corda.testing.CoreTestUtils.unsetCordappPackages;

public class FlowTests {
    private MockNetwork network;
    private StartedNode<MockNetwork.MockNode> a;
    private StartedNode<MockNetwork.MockNode> b;
    private StartedNode<MockNetwork.MockNode> c;

    @Before
    public void setup() {
        setCordappPackages("com.template");
        network = new MockNetwork();
        MockNetwork.BasketOfNodes nodes = network.createSomeNodes(3);
        a = nodes.getPartyNodes().get(0);
        b = nodes.getPartyNodes().get(1);
        c = nodes.getPartyNodes().get(2);
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        for (StartedNode<MockNetwork.MockNode> node : nodes.getPartyNodes()) {
            node.registerInitiatedFlow(CreateFlowResponder.class);
            node.registerInitiatedFlow(ShareFlowResponder.class);
        }

        network.runNetwork();
//        notary = a.services.getDefaultNotary();
    }

    @After
    public void tearDown() {
        network.stopNodes();
        unsetCordappPackages();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void createAndShare() throws Exception {
        String link = "abc";
        SharedItemCreateFlow createFlow = new SharedItemCreateFlow(b.getInfo().getLegalIdentities().get(0), link);
        CordaFuture<SignedTransaction> createFlowFuture = a.getServices().startFlow(createFlow).getResultFuture();
        network.runNetwork();

        SignedTransaction signedCreateFlowTx = createFlowFuture.get();
        signedCreateFlowTx.verifyRequiredSignatures();

        String createSharedItemTxId = signedCreateFlowTx.getId().toString();
        SharedItemShareFlow shareFlow = new SharedItemShareFlow(c.getInfo().getLegalIdentities().get(0), createSharedItemTxId);
//        CordaFuture<SignedTransaction> shareFlowFuture = a.getServices().startFlow(shareFlow).getResultFuture();
        CordaFuture<Void> shareFlowFuture = a.getServices().startFlow(shareFlow).getResultFuture();
        network.runNetwork();

//        SignedTransaction signedShareFlowTx = shareFlowFuture.get();
//        signedShareFlowTx.verifyRequiredSignatures();

        SignedTransaction txCHas = c.getServices().getValidatedTransactions().getTransaction(SecureHash.parse(createSharedItemTxId));
        assert(txCHas != null);
        List<TransactionSignature> sigs = txCHas.getSigs();
        List<Party> signatories = new ArrayList<>(sigs.size());
        IdentityService identities = c.getServices().getIdentityService();
        for (TransactionSignature sig: sigs) {
            Party party = identities.certificateFromKey(sig.getBy()).component1();
            signatories.add(party);
        }

        assert(signatories.contains(a.getInfo().getLegalIdentities().get(0)));
        assert(signatories.contains(b.getInfo().getLegalIdentities().get(0)));
        assert(!signatories.contains(c.getInfo().getLegalIdentities().get(0)));
        // notary
        assert(signatories.contains(a.getServices().getNetworkMapCache().getNotaryIdentities().get(0)));

        SharedItemState stateCHas = (SharedItemState) txCHas.getTx().getOutputs().get(0).getData();
//        List<StateAndRef<SharedItemState>> statesCHas = c.getServices().getVaultService().queryBy(SharedItemState.class).getStates();
//        SharedItemState stateCHas = statesCHas.get(0).getState().component1();
        assert(stateCHas.getLink().equals(link));
    }
}
