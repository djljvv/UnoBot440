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
        //necessary for argmaxQValues, do not touch
        this.LastDrawnCardIdx = drawnCardIdx;
        this.PresentGameView = game;

        //minimax from lab but edited given three things, initated below and then ran in helper
        int CurrentPlayer = game.getPlayerOrder().getCurrentLogicalPlayerIdx();
        //starting point for the tree based on the current player to max max max
        Node root = new MCTSNode(game, CurrentPlayer, null);

        minimaxedSearch(root);
        

        return root;
    }

    //helper recurive method for modded minimax
    private float[] minimaxedSearch(Node node){
        int numPlayers = this.getNumPlayers(node.getGameView());

        //we're at a known terminal state so just return
        if(node.isTerminal()){
            //terminal state is when one player has no cards left, so we check for that and assign 1.0 to the winner and 0.0 to everyone else
            float[] terminalScores = new float[numPlayers];

            for(int i = 0; i < numPlayers; ++i){
                if(node.getGameView().getHandView(i).size() == 0){
                    terminalScores[i] = 1.0f;
                }
                else{
                    terminalScores[i] = 0.0f;
                }
            }

            return terminalScores;
        }

        //basically tells us given the node/player we are at, what are our possibilities
        int numActions;
        if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES){
            numActions = node.getOrderedLegalMoves().size();
        }
        else if(node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT){
            numActions = 1;
        }
        else{
            numActions = 2;
        }

        //what player are we maxing for given they are the parent
        int currentPlayer = node.getLogicalPlayerIdx();
        //best qvector retunred so far initalized(parent)
        float[] bestScores = null;
        //player component from best vector(parent)
        float winningScore = Float.NEGATIVE_INFINITY;

        //loops actions
        for(int i = 0; i < numActions; ++i){
            //build out the move
            Move move = null;

            //make hand given legal moves
            if(node.getNodeState() == Node.NodeState.HAS_LEGAL_MOVES){
                int cardIdx = node.getOrderedLegalMoves().get(i);
                HandView hand = node.getGameView().getHandView(node.getLogicalPlayerIdx());
                Card card = hand.getCard(cardIdx);
                move = this.makeMoveFromCard(cardIdx, card, hand, null);
            }
            else if(node.getNodeState() == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD){
                //if we wanna play
                if(i == 0){
                    move = Move.createMove(this, 0);
                }
            }

            //parent "recurses" on child but ignore the fact we are iterating
            Node child = node.getChild(move);
            if(child == null){
                continue;
            }

            //this is the q-vector for the child node, and we check the children of it
            float[] childScores = minimaxedSearch(child);
            //comparison value used by the parent, i.e. player one looks at its q values, from what is has what is best for it, do same for all other players who are at n given node
            float thisPlayerScore = childScores[currentPlayer];

            //update q-values in q-vector for the current node based on the child scores, this is where players have to "deal" with choices of others(from andrew woods office hours)
            node.setQCount(i, node.getQCount(i) + 1);
            node.setQValueTotal(i, node.getQValueTotal(i) + thisPlayerScore);

            //best child from parents perspective, propogating up node values
            if(bestScores == null || thisPlayerScore > winningScore){
                bestScores = childScores;
                winningScore = thisPlayerScore;
            }
        }

        //no children works = this guy banned child labor
        if(bestScores == null){
            return new float[numPlayers];
        }

        //if you don't know what this means... dawg come on
        return bestScores;
    }

    private int getNumPlayers(final GameView game){
        int numPlayers = 0;

        while(true){
            try{
                game.getHandView(numPlayers);
                ++numPlayers;
            }
            catch(Exception e){
                break;
            }
        }

        return numPlayers;
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

    //color fix
    private Color chooseWildColor(final HandView hand){
        int redCount = 0;
        int asianCount = 0;
        //what color are asians
        int greenCount = 0;
        int blueCount = 0;

        for(int i = 0; i < hand.size(); ++i){
            Card card = hand.getCard(i);

            if(card.color() == Color.RED){
                ++redCount;
            }
            else if(card.color() == Color.YELLOW){
                ++asianCount;
            }
            else if(card.color() == Color.GREEN){
                ++greenCount;
            }
            else if(card.color() == Color.BLUE){
                ++blueCount;
            }
        }

        Color bestColor = Color.RED;
        int bestCount = redCount;

        if(asianCount > bestCount){
            bestColor = Color.YELLOW;
            bestCount = asianCount;
        }
        if(greenCount > bestCount){
            bestColor = Color.GREEN;
            bestCount = greenCount;
        }
        if(blueCount > bestCount){
            bestColor = Color.BLUE;
        }

        return bestColor;
    }

}
