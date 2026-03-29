package src.pas.UnoBot440.agents;


// SYSTEM IMPORTS
import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game.GameView;
import edu.bu.pas.uno.Hand.HandView;
import edu.bu.pas.uno.agents.MCTSAgent;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.moves.Move;
import edu.bu.pas.uno.tree.Node;
import edu.bu.pas.uno.tree.Node.NodeState;
import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.Hand;

import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;


// JAVA PROJECT IMPORTS


public class UCTAgent
    extends MCTSAgent
{

    public static class MCTSNode
        extends Node
    {
        Map<Move, Node> children;

        public MCTSNode(final GameView game,
                        final int logicalPlayerIdx,
                        final Node parent)
        {
            super(game, logicalPlayerIdx, parent);
            children = new HashMap<Move, Node>();
        }

        public Map<Move, Node> getChildren() {
            return children;
        }

        @Override
        public Node getChild(final Move move)
        {
            // Trying to maintain a mapping to the child nodes
            if (getChildren().containsKey(move)) {
                return getChildren().get(move);
            }

            // Create a new game object based on the game view of the current node
            Game g =  new Game(this.getGameView());

            Hand hand = g.getHand(getLogicalPlayerIdx());
            
            System.out.println("Trying to resolve move");
            // Resolve the move which also moves to the next player
            g.resolveMove(move);

            System.out.println("Move resolved");
            // Use the updated game object with the resolved move to create the child node
            Node child = new MCTSNode(g.getView(g.getCurrentAgent().getPlayerIdx()), g.getCurrentAgent().getLogicalPlayerIdx(), this);
            getChildren().put(move, child);
            System.out.println("Child node created and added to the map");
            return child;
        }
    }

    public UCTAgent(final int playerIdx,
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
        // Create the root node
        Node root = new MCTSNode(game, game.getPlayerOrder().getCurrentLogicalPlayerIdx(), null);

        // Used for determing whether a particular rollout was won by the player we control
        int playerIdx = root.getLogicalPlayerIdx();

        // Iterate for some constant number of expansions
        for (int i = 0; i < 10; i++) {
            // This method just expands one node at a time
            System.out.println("Current i value: " + i);
            root = expandNode(root, playerIdx);
        }

        return root;
    }

    public Node expandNode(Node cNode, int playerIdx) {
        // Node for traversing through the tree until an unexpanded node is found
        Node trav = cNode;

        // Used to end the traversal
        boolean newNode = false;

        // Used for backtracking back up the tree after a rollout for updating qvalues
        Stack<Move> s = new Stack<Move>();

        while (!newNode) {
            System.out.println(trav.getNodeState());
            if (trav.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
                // Nothing to be done here although there's maybe something that needs updates for actually manually drawing cards
                Move m = Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX);

                // If the qCount is 0 then the node is unexpanded so end the while loop to expand that node
                if (trav.getQCount(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX) == 0) {
                    s.push(m);
                    trav = trav.getChild(m);
                    newNode = true;
                } else {
                    s.push(m);
                    trav = trav.getChild(m);
                }
            } else if (trav.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
                int keepIdx = Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX;
                int playIdx = Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX;

                // In either of the first cases, one of the two hasn't yet been expanded so we just expand that node
                if (trav.getQCount(keepIdx) == 0) {
                    Move m = Move.createMove(this, keepIdx);
                    s.push(m);
                    trav = trav.getChild(Move.createMove(this, keepIdx));
                    newNode = true;
                } else if (trav.getQCount(playIdx) == 0) {
                    Move m = Move.createMove(this, playIdx, Color.BLUE);
                    s.push(m);
                    trav = trav.getChild(m);
                    newNode = true;
                } else {
                    // In this final case we use UCT to choose the better option
                    int idx = exploreIdx(trav);
                    Move m = Move.createMove(this, idx, Color.BLUE);
                    trav = trav.getChild(m);
                }
            } else {
                // Case where we have legal moves and don't need to draw a card
                int i = 0;
                System.out.println("Reaching legal move point in while loop");
                // boolean used to determine whether all legal moves have been explored at least once
                boolean finishedLayer = true;
                for (Integer j : trav.getOrderedLegalMoves()) {
                    if (trav.getQCount(i) == 0) {
                        // If this is the case then we need to expand this unexplored node
                        Move m = Move.createMove(this, j);
                        System.out.println("Successfully created move");
                        s.push(m);
                        trav = trav.getChild(m);

                        System.out.println("trav updated to child");
                        newNode = true;
                        finishedLayer = false;
                        break;
                    }
                    i++;
                }
                System.out.println("Reaching end of for loop in legal move point in while loop");
                if (finishedLayer == true) {
                    // If the boolean was never set to false then we just use UCT to choose the best option
                    int idx = exploreIdx(trav);
                    Move m = Move.createMove(this, idx, Color.BLUE);
                    s.push(m);
                    trav = trav.getChild(Move.createMove(this, idx));
                }
            }
        }
        System.out.println("Starting rollout");
        // Expand the node through a rollout
        int outcomeIdx = rollout(trav);
        System.out.println("You are player: " + playerIdx + " and this rollout was won by player: " + outcomeIdx);

        // Keep backtracking using the parent pointers until the root
        while (trav != null) {
            // Go up to parent
            trav = trav.getParent();

            // Get the move used to reach the next node via the stack
            Move m = s.pop();
            int moveIdx = m.getCardToPlayIdx();

            // Update the count
            trav.setQCount(moveIdx, trav.getQCount(moveIdx) + 1);

            // If our player won, then update the total
            if (outcomeIdx == playerIdx) {
                trav.setQValueTotal(moveIdx, trav.getQValueTotal(moveIdx) + 1);
            }
        }

        // Return the root node
        return cNode;
    }

    public float calc(int idx, Node cNode, long total) {
        // Just performing the bound calculation for a particular action
        float x_a = cNode.getQValue(idx);
        float sq = (float) Math.sqrt(2.0 * Math.log(total) / cNode.getQCount(idx));
        return x_a + sq;
    }

    public int exploreIdx(Node cNode) {
        if (cNode.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
            int keepIdx = Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX;
            int playIdx = Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX;

            // Calculate the total number of times this node has been visited
            long total = cNode.getQCount(keepIdx) + cNode.getQCount(playIdx); 

            // Use the helper function above to perform the simple argmax
            if (calc(keepIdx, cNode, total) > calc(playIdx, cNode, total)) {
                return keepIdx;
            } else {
                return playIdx;
            }
        }
        
        // If we reach this point we must have legal moves; We don't call this in the case of unresolved cards that need to be drawn
        long total = 0;
        // Calculate the total number of times this node has been visited
        for (int i = 0; i < cNode.getOrderedLegalMoves().size(); i++) {
            total += cNode.getQCount(i);
        }

        int j = 0;
        int maxIdx = -1;
        float maxBound = -1;
        // Use the helper method and conditional logic to perform the argmax
        for (Integer i : cNode.getOrderedLegalMoves()) {
            float bound = calc(j, cNode, total);
            j += 1;
            if (maxBound == -1 || bound > maxBound) {
                maxBound = bound;
                maxIdx = i;
            }
        }

        return maxIdx;
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
        Node.NodeState state = node.getNodeState();

        if (state == Node.NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
            // Nothing to be done
            return Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX);
        } else if (state == Node.NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
            // Get Q values based on the move indices that are preset for this particular state
            double playVal = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
            double keepVal = node.getQValue(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX);

            GameView gameView = node.getGameView();
            HandView h = gameView.getHandView(getLogicalPlayerIdx());

            if (playVal > keepVal) {
                // Get the card that was drawn
                Card c = h.getCard(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);

                // If we have a wildcard then just choose whichever color we have the most of currently
                if (c.isWild()) {
                    int numCards = h.size();
                    int countBlue = 0;
                    int countGreen = 0;
                    int countRed = 0;
                    int countYellow = 0;
                    for (int i = 0; i < numCards - 1; i++) {
                        if (h.getCard(i).color() == Color.BLUE) {
                            countBlue++;
                        }
                        if (h.getCard(i).color() == Color.GREEN) {
                            countGreen++;
                        }
                        if (h.getCard(i).color() == Color.RED) {
                            countRed++;
                        }
                        if (h.getCard(i).color() == Color.YELLOW) {
                            countYellow++;
                        }
                    }
                    Color color = null;
                    int maxCount = 0;
                    if (countBlue > maxCount) {
                        maxCount = countBlue;
                        color = Color.BLUE;
                    }
                    if (countGreen > maxCount) {
                        maxCount = countGreen;
                        color = Color.GREEN;
                    }
                    if (countRed > maxCount) {
                        maxCount = countRed;
                        color = Color.RED;
                    }
                    if (countYellow > maxCount) {
                        maxCount = countYellow;
                        color = Color.YELLOW;
                    }

                    return Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX, color);
                }

                return Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
            } else {
                return null;
            }
        } else {
            int bestIdx = -1;
            float bestQVal = -1;  
            int numMoves = node.getOrderedLegalMoves().size();

            // Perform simple argmax on the legal moves
            for (int i = 0; i < numMoves; i++) {
                if (i == 0) {
                    bestIdx = node.getOrderedLegalMoves().get(0);
                    bestQVal = node.getQValue(i);
                } else if (node.getQValue(i) > bestQVal) {
                    bestQVal = node.getQValue(i);
                    bestIdx = node.getOrderedLegalMoves().get(i);
                }
            }

            return Move.createMove(this, bestIdx);
        }
    }

    // Return the logical player index of the winning player from this rollout
    public int rollout(Node n) {
        // If the node is terminal return the current players index
        if (n.isTerminal()) {
            return n.getLogicalPlayerIdx();
        }

        if (n.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
            // Just recurse on the next player with no card played
            return rollout(n.getChild(Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX)));
        } else if (n.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
            // Randomly choose between keeping or playing drawn card. Also choose random color if wild
            int choice = getRandom().nextInt(2);
            if (choice == 0) {
                return rollout(n.getChild(Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX)));
            } else {
                Card c = n.getGameView().getHandView(getLogicalPlayerIdx()).getCard(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
                if (c.isWild()) {
                    return rollout(n.getChild(Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX, Color.getRandomColor(getRandom()))));
                }
                return rollout(n.getChild(Move.createMove(this, Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX)));
            }
        } else if (n.getNodeState() == NodeState.HAS_LEGAL_MOVES) {
            // Choose random legal move
            int choice = getRandom().nextInt(n.getOrderedLegalMoves().size());
            Card c = n.getGameView().getHandView(getLogicalPlayerIdx()).getCard(n.getOrderedLegalMoves().get(choice));
            if (c.isWild()) {
                // Use random color if wild card
                return rollout(n.getChild(Move.createMove(this, n.getOrderedLegalMoves().get(choice), Color.getRandomColor(getRandom()))));
            }
            return rollout(n.getChild(Move.createMove(this, n.getOrderedLegalMoves().get(choice))));
        } else {
            return rollout(n.getChild(null));
        }
    }
}