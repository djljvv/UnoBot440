package src.pas.uno.agents;


// SYSTEM IMPORTS
import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game.GameView;
import edu.bu.pas.uno.Hand.HandView;
import edu.bu.pas.uno.agents.MCTSAgent;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.moves.Move;
import edu.bu.pas.uno.tree.Node;

import java.util.Random;
import java.util.Set;


// JAVA PROJECT IMPORTS


public class ExpectedOutcomeAgent
    extends MCTSAgent
{
    private Integer LastDrawnCardIdx;
    private GameView PresentGameView;

    public static class MCTSNode
        extends Node
    {

        public MCTSNode(final GameView game,
                        final int logicalPlayerIdx,
                        final Node parent)
        {
            super(game, logicalPlayerIdx, parent);
        }

        @Override
        public Node getChild(final Move move)
        {

            return null;
        }
    }

    public ExpectedOutcomeAgent(final int playerIdx,
                                final long maxThinkingTimeInMS)
    {
        super(playerIdx, maxThinkingTimeInMS);
    }

    /**
     * A method to perform the MCTS search on the game tree
     *
     * @param   game            The {@link GameView} that should be the root of the game tree
     * @param   drawnCardIdx    This will be non-null when this method is being called by the 
     *                          <code>maybePlayDrawnCard</code> method of {@link Agent} and will
     *                          be <code>null</code> when being called by <code>chooseCardToPlay</code>
     *                          method of {@link Agent}
     * @return  The {@link Node} of the root who'se q-values should now be populated and ready to argmax
     */
    @Override
    public Node search(final GameView game,
                       final Integer drawnCardIdx)
    {

        // TODO: implement me!
        this.LastDrawnCardIdx = drawnCardIdx;
        this.PresentGameView = game;
        return null;
    }

    /**
     * A method to argmax the Q values inside a {@link Node}
     *
     * @param   node            The {@link Node} who has populated q-values
     * @return  The {@link Move} corresponding to whichever {@link Move} has the largest q-value. Note
     *          that this can be <code>null</code> if you choose to not play the drawn card (you will
     *          have to detect whether or not you are in that scenario by examining the @{link Node}'s state).
     */
    @Override
    public Move argmaxQValues(final Node node)
    {
        // TODO: implement me!

        //initialize best action, numactions, and best q-value
        int bestActionIdx = 0;
        float maxQValue = Float.NEGATIVE_INFINITY;
        final int numActions;

        if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES){
            numActions = node.getOrderedLegalMoves().size();
        }
        else if(node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT){
            numActions = 1;
        }
        else{
            numActions = 2;
        }

        //basically the loop here is finding the highest Q-value by looking at every current legal action
        //certain cases as shown above will have 1,2, or more actions depending on the node we are at
        //
        for(int i = 0; i < numActions; ++i){
            if(node.getQCount(i) == 0){
                continue;
            }

            float qValue = node.getQValue(i);
            //basic ass max function
            if(qValue > maxQValue){
                maxQValue = qValue;
                bestActionIdx = i;
            }
        }

        if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES){
            //where are we in the legal moves
            int cardIdx = node.getOrderedLegalMoves().get(bestActionIdx);
            //get the hand corresponding to the node
            HandView za_hando = node.getGameView().getHandView(node.getLogicalPlayerIdx());
            //get the card corresponding to the card index
            Card card = za_hando.getCard(cardIdx);
            //no this are not chatGPT comments, im going crazy at 3:42 in the morning as ive rewritten this 8 times
            return this.makeMoveFromCard(cardIdx, card, za_hando, null);
        }
        if(node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT){
            return null;
        }
        if(node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD){
            //play(0) or keep(1) new card
            //check q-values for both options and return the one with the higher q-value(add this in later)
            if(bestActionIdx == 1){
                return null;
            }//im figuring this out but essentially we should be saying that given we are playing the drawncard, whats optimal
            if(this.LastDrawnCardIdx == null || this.PresentGameView == null){
                throw new IllegalStateException("you got cooked by literally nothing");
            }
            else{
                //ngl i think i just return the card as its playing it but idk
                HandView za_hando = this.PresentGameView.getHandView(node.getLogicalPlayerIdx());
                Card card = za_hando.getCard(this.LastDrawnCardIdx);
                return this.makeMoveFromCard(this.LastDrawnCardIdx, card, za_hando, null);
            }
        }
        else{
            throw new IllegalStateException("the result was not expected" + node.getNodeState());
        }


        return null;
    }

    //created from insanity, I'm not going to lie I found random parts of the library and im assuming they hopefully proabably work
    private Move makeMoveFromCard(final int cardIdx, final Card card, final HandView hand, final Color forcedColor){
        if(!card.isWild())
        {
            return Move.createMove(this, cardIdx);
        }
        //add choose wild color logic in mornin
        Color wildColor;
        if(forcedColor != null){
            wildColor = forcedColor;
        }
        else{
            wildColor = this.chooseWildColor(hand);
        }
        return Move.createMove(this, cardIdx, wildColor);
    }

}
