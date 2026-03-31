package src.pas.UnoBot440.agents;


// SYSTEM IMPORTS
import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Deck;
import edu.bu.pas.uno.Game.GameView;
import edu.bu.pas.uno.Hand.HandView;
import edu.bu.pas.uno.agents.Agent;
import edu.bu.pas.uno.agents.MCTSAgent;
import edu.bu.pas.uno.agents.RandomAgent;
import edu.bu.pas.uno.enums.Observability;
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
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;


// JAVA PROJECT IMPORTS


public class UCTAgent
    extends MCTSAgent
{
    private static long nodesCreated = 0;
    private Integer lastDrawnCardIdx;
    private GameView presentGameView;
    private final long searchBudgetInMs;

    public static class MCTSNode
        extends Node
    {
        Map<Integer, Node> children;

        public MCTSNode(final GameView game,
                        final int logicalPlayerIdx,
                        final Node parent)
        {
            super(game, logicalPlayerIdx, parent);
            children = new HashMap<Integer, Node>();
            nodesCreated += 1;
        }

        public Map<Integer, Node> getChildren() {
            return children;
        }

        @Override
        public Node getChild(final Move move)
        {
            if (move == null && getChildren().containsKey(-1)) {
                //System.out.println("Reusing move: " + move);
                return getChildren().get(-1);
            }
            if (move != null && getChildren().containsKey(move.getCardToPlayIdx())) {
                //System.out.println("Reusing move: " + move);
                return getChildren().get(move.getCardToPlayIdx());
            }

            Game g = constructGame(getGameView());
            boolean resolvedNull = false;

            if (move == null) {
                g.resolveMove(null);
            } else if (this.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
                if (getChildren().containsKey(-1)) {
                    return getChildren().get(-1);
                }
                Hand h = g.getCurrentPlayerHand();
                g.drawTotal(h, g.getUnresolvedCards().total());
                g.getUnresolvedCards().clear();
                resolvedNull = true;
                g.resolveMove(null);
            } else if (this.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
                int dIdx = g.drawCard(g.getCurrentPlayerHand());
                Card c = g.getCurrentPlayerHand().getCard(dIdx);
                if (!c.canBePlayedAsDrawCard(g) || move.getCardToPlayIdx() == Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX) {
                    if (getChildren().containsKey(-1)) {
                        return getChildren().get(-1);
                    }
                    resolvedNull = true;
                    g.resolveMove(null);
                } else {
                    if (c.isWild()) {
                        g.resolveMove(Move.createMove(g.getAgent(getLogicalPlayerIdx()), dIdx, bestColor(getGameView().getHandView(getLogicalPlayerIdx()))));
                    } else {    
                        g.resolveMove(Move.createMove(g.getAgent(getLogicalPlayerIdx()), dIdx));
                    }
                }
            } else if (this.getNodeState() == NodeState.HAS_LEGAL_MOVES) {
                g.resolveMove(move);
            }

            int nextPlayer = g.getPlayerOrder().getCurrentLogicalPlayerIdx();
            Node child = new MCTSNode(g.getOmniscientView(), nextPlayer, this);
            if (move != null && resolvedNull == false) {
                getChildren().put(move.getCardToPlayIdx(), child);
            } else {
                getChildren().put(-1, child);
            }
            
            return child;
        }
    }

    public UCTAgent(final int playerIdx,
                    final long maxThinkingTimeInMS)
    {
        super(playerIdx, maxThinkingTimeInMS);
        this.searchBudgetInMs = maxThinkingTimeInMS;
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
        this.lastDrawnCardIdx = drawnCardIdx;
        this.presentGameView = game;
        nodesCreated = 0;

        System.out.println("Starting search");
        Game rootGame = gameSim(game);
        System.out.println("Simulating game");
        Node root = new MCTSNode(rootGame.getOmniscientView(),
                                rootGame.getPlayerOrder().getCurrentLogicalPlayerIdx(),
                                null);
        if (root.getNodeState() == NodeState.HAS_LEGAL_MOVES || root.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD || root.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
            System.out.println(root.getNodeState());
        } else {
            System.out.println("Forced to draw card but can't play it");
        }

        //used to deterimine which player's perspective we are doing the search from for the backpropagation step
        int playerIdx = this.getLogicalPlayerIdx();
        long startTime = System.currentTimeMillis();

        //time based iteration so we can figure out the whole thing
        while (!Thread.currentThread().isInterrupted()
            && System.currentTimeMillis() - startTime < this.searchBudgetInMs - 100) {
            expandNode(root, playerIdx);
        }
        System.out.println("Returning from search with nodes created: " + nodesCreated);
        return root;
    }

    public Node expandNode(Node cNode, int playerIdx) {
        // Node for traversing through the tree until an unexpanded node is found
        Node trav = cNode;

        // Used to end the traversal
        boolean newNode = false;

        // Used for backtracking back up the tree after a rollout for updating qvalues
        Stack<Integer> s = new Stack<Integer>();

        while (!newNode) {
            if (trav.isTerminal()) {
                break;
            }

            if (trav.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
                // Nothing to be done here although there's maybe something that needs updates for actually manually drawing cards
                Move m = Move.createMove(constructGame(trav.getGameView()).getAgent(trav.getLogicalPlayerIdx()), Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX);

                // If the qCount is 0 then the node is unexpanded so end the while loop to expand that node
                if (trav.getQCount(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX) == 0) {
                    s.push(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX);
                    trav = trav.getChild(m);
                    newNode = true;
                } else {
                    s.push(Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX);
                    trav = trav.getChild(m);
                }
            } else if (trav.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
                int keepIdx = Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX;
                int playIdx = Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX;

                // In either of the first cases, one of the two hasn't yet been expanded so we just expand that node
                if (trav.getQCount(keepIdx) == 0) {
                    //Move m = Move.createMove(this, keepIdx);
                    s.push(keepIdx);
                    trav = trav.getChild(makeTreeMove(trav, keepIdx, false));
                    newNode = true;
                } else if (trav.getQCount(playIdx) == 0) {
                    Move m = makeTreeMove(trav, playIdx, false);
                    s.push(playIdx);
                    trav = trav.getChild(m);
                    newNode = true;
                } else {
                    // In this final case we use UCT to choose the better option
                    int idx = exploreIdx(trav);
                    //main changes are that we are now pushing based on the helper fucntion
                    s.push(idx);
                    Move m = makeTreeMove(trav, idx, false);
                    trav = trav.getChild(m);
                }
            } else {
                // Case where we have legal moves and don't need to draw a card
                int i = 0;
                // boolean used to determine whether all legal moves have been explored at least once
                boolean finishedLayer = true;
                for (Integer j : trav.getOrderedLegalMoves()) {
                    if (trav.getQCount(i) == 0) {
                        // If this is the case then we need to expand this unexplored node
                        Move m = makeTreeMove(trav, j, false);
                        s.push(i);
                        trav = trav.getChild(m);

                        newNode = true;
                        finishedLayer = false;
                        break;
                    }
                    i++;
                }
                if (finishedLayer == true) {
                    // If the boolean was never set to false then we just use UCT to choose the best option
                    int idx = exploreIdx(trav);
                    Move m = makeTreeMove(trav, idx, false);
                    for (int j = 0; j < trav.getOrderedLegalMoves().size(); j++) {
                        if (trav.getOrderedLegalMoves().get(j) == idx) {
                            s.push(j);
                            break;
                        }
                    }
                    trav = trav.getChild(m);
                }
            }
        }

        int outcomeIdx = rollout(trav);

        // Keep backtracking using the parent pointers until the root
        while (trav.getParent() != null) {
            //get move index from stack so we can update corrent move values
            int moveIdx = s.pop(); 
            Node travParent = trav.getParent();
            // if (travParent.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
            //     moveIdx = 0;
            // } else if (travParent.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
            //     if (moveIdx == Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX) {
            //         moveIdx = 0;
            //     } else {
            //         moveIdx = 1;
            //     }
            // }

            // Update the count
            travParent.setQCount(moveIdx, travParent.getQCount(moveIdx) + 1);

            // If our player won, then update the total
            if (outcomeIdx == playerIdx) {
                travParent.setQValueTotal(moveIdx, travParent.getQValueTotal(moveIdx) + 1);
            }
            //update the node
            trav = travParent;
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

        //int j = 0;
        int maxIdx = -1;
        float maxBound = -1;
        // Use the helper method and conditional logic to perform the argmax
        for (int i = 0; i < cNode.getOrderedLegalMoves().size(); i++) {
            float bound = calc(i, cNode, total);
            if (maxBound == -1 || bound > maxBound) {
                maxBound = bound;
                maxIdx = cNode.getOrderedLegalMoves().get(i);
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
            // double playVal = node.getQValue(1);
            // double keepVal = node.getQValue(0);

            GameView gameView = node.getGameView();
            HandView h = this.presentGameView.getHandView(node.getLogicalPlayerIdx());

            if (playVal > keepVal) {
                // Get the card that was drawn
                Card c = h.getCard(this.lastDrawnCardIdx);

                // If we have a wildcard then just choose whichever color we have the most of currently
                if (c.isWild()) {
                    return Move.createMove(this, this.lastDrawnCardIdx, bestColor(h));
                }

                return Move.createMove(this, this.lastDrawnCardIdx);
            } else {
                return null;
            }
        } else if (state == Node.NodeState.HAS_LEGAL_MOVES) {
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

            //added this in, it basically just checks if the card we are trying to play is a wild card, and if it is then we choose the color we have the most of in our hand and play that move instead of just playing the card with a random color
            HandView h = node.getGameView().getHandView(node.getLogicalPlayerIdx());
            Card c = h.getCard(bestIdx);

            if (c.isWild()) {
                return Move.createMove(this, bestIdx, bestColor(h));
            }

            return Move.createMove(this, bestIdx);
        } else {
            return null;
        }
    }

    // Return the logical player index of the winning player from this rollout
    public int rollout(Node n) {
        // If the node is terminal return the current players index
        if (n.isTerminal()) {
            return findWinningPlayer(n.getGameView());
            //return n.getLogicalPlayerIdx();
        }

        Game g = constructGame(n.getGameView());

        if (n.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
            // Just recurse on the next player with no card played
            return rollout(n.getChild(Move.createMove(g.getAgent(n.getLogicalPlayerIdx()), Node.NoLegalMovesIdxDefaults.DrawUnresolvedCardsIdxs.MOVE_IDX)));
        } else if (n.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {

            // Randomly choose between keeping or playing drawn card. Also choose random color if wild
            int choice = getRandom().nextInt(2);
            if (choice == 0) {
                return rollout(n.getChild(Move.createMove(g.getAgent(n.getLogicalPlayerIdx()), Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX)));
            } else {
                Card c = g.getCurrentPlayerHand().getCard(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
                if (c.canBePlayedAsDrawCard(g)) {
                    if (c.isWild()) {
                        return rollout(n.getChild(Move.createMove(g.getAgent(n.getLogicalPlayerIdx()), Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX, Color.getRandomColor(getRandom()))));
                    }
                    return rollout(n.getChild(Move.createMove(g.getAgent(n.getLogicalPlayerIdx()), Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX)));
                } else {
                    return rollout(n.getChild(null));
                }
            }
        } else if (n.getNodeState() == NodeState.HAS_LEGAL_MOVES) {
            // Choose random legal move
            int choice = getRandom().nextInt(n.getOrderedLegalMoves().size());
            Card c = g.getCurrentPlayerHand().getCard(n.getOrderedLegalMoves().get(choice));
            if (c.isWild()) {
                // Use random color if wild card
                return rollout(n.getChild(Move.createMove(g.getAgent(n.getLogicalPlayerIdx()), n.getOrderedLegalMoves().get(choice), Color.getRandomColor(getRandom()))));
            }
            return rollout(n.getChild(Move.createMove(g.getAgent(n.getLogicalPlayerIdx()), n.getOrderedLegalMoves().get(choice))));
        } else {
            return rollout(n.getChild(null));
        }
    }

    //helper function to create a move based on the node and action index given, also takes in boolean to determine if we are doing random colors or not
    private Move makeTreeMove(final Node node, final int actionIdx, final boolean randomColor) {
        Game g = constructGame(node.getGameView());
        Agent curAgent = g.getAgent(node.getLogicalPlayerIdx());
        //check if we have no legal moves and still need to draw a a card
        if (node.getNodeState() == NodeState.NO_LEGAL_MOVES_UNRESOLVED_CARDS_PRESENT) {
            return Move.createMove(curAgent, actionIdx);
        }

        //check if we have no legal moves but can play the drawn card
        if (node.getNodeState() == NodeState.NO_LEGAL_MOVES_MAY_PLAY_DRAWN_CARD) {
            if (actionIdx == Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.KEEP_CARD_MOVE_IDX) {
                return Move.createMove(curAgent, actionIdx);
            }

            //initalize hand view and drawn card based on the node's game view and logical player index
            HandView h = node.getGameView().getHandView(node.getLogicalPlayerIdx());
            //Card c = h.getCard(h.size() - 1);
            Card c = h.getCard(Node.NoLegalMovesIdxDefaults.DrawSingleCardIdxs.PLAY_CARD_MOVE_IDX);
            //if the drawn card is wild then we need to choose a color for the move
            if (c.isWild()) {
                Color color;
                if (randomColor) {
                    color = Color.getRandomColor(getRandom());
                } else {
                    color = bestColor(h);
                }

                return Move.createMove(curAgent, actionIdx, color);
            }

            return Move.createMove(curAgent, actionIdx);
        }

        //initalize card index based on the action index and the node's ordered legal moves
        int cardIdx = actionIdx;
        //int cardIdx = node.getOrderedLegalMoves().get(actionIdx);
        HandView h = node.getGameView().getHandView(node.getLogicalPlayerIdx());
        Card c = h.getCard(cardIdx);

        //again color randomizer for wild cards
        if (c.isWild()) {
            Color color;
            if (randomColor) {
                color = Color.getRandomColor(getRandom());
            } else {
                color = bestColor(h);
            }
            return Move.createMove(curAgent, cardIdx, color);
        }
        return Move.createMove(curAgent, cardIdx);
    }


    //color helper function
    private static Color bestColor(final HandView h) {
        int countBlue = 0;
        int countGreen = 0;
        int countRed = 0;
        //yk
        int countYellow = 0;

        for (int i = 0; i < h.size(); i++) {
            Card card = h.getCard(i);

            if (card.isWild()) {
                continue;
            }

            if (card.color() == Color.BLUE) countBlue++;
            if (card.color() == Color.GREEN) countGreen++;
            if (card.color() == Color.RED) countRed++;
            if (card.color() == Color.YELLOW) countYellow++;
        }


        Color color = Color.BLUE;
        int maxCount = countBlue;

        if (countGreen > maxCount) {
            maxCount = countGreen;
            color = Color.GREEN;
        }
        if (countRed > maxCount) {
            maxCount = countRed;
            color = Color.RED;
        }
        if (countYellow > maxCount) {
            color = Color.YELLOW;
        }
        //System.out.println(color);
        return color;
    }

    //helper function to find the logical player index of the winning player from a terminal game view
    private int findWinningPlayer(final GameView gameView) {
        int i = 0;

        //just loops through player indicies until theres an empty hand found
        while (true) {
            try {
                if (gameView.getHandView(i).size() == 0) {
                    return i;
                }
                i++;
            } catch (Exception e) {
                break;
            }
        }

        return -1;
    }

    // Helper to construct game objects from a game view
    public static Game constructGame(GameView gView) {
        Agent[] a = new Agent[gView.getNumPlayers()];
        Hand[] hands = new Hand[gView.getNumPlayers()];
        for (int i = 0; i < a.length; i++) {
            int pIdx = gView.getPlayerOrder().getAgentIdx(i);
            a[i] = new UCTAgent(pIdx, Long.MAX_VALUE);
            hands[i] = new Game(gView).getHand(i);
        }

        Game g = new Game(gView.getDrawPile(), hands, Observability.PARTIAL_NO_DECK, gView, a);
        return g;
    }

    //makes fake agents to play against for sake of the simulations
    private Agent[] fakeAgent(final GameView view) {
        int numPlayers = view.getNumPlayers();
        Agent[] agents = new Agent[numPlayers];

        for (int logicalIdx = 0; logicalIdx < numPlayers; logicalIdx++) {
            int playerIdx = view.getPlayerOrder().getAgentIdx(logicalIdx);
            agents[logicalIdx] = new RandomAgent(playerIdx, 0L);
        }

        return agents;
    }


    //simulates a game based on various unknowns, although we kinda can already see everything
    private Game gameSim(final GameView view) {
        int numPlayers = view.getNumPlayers();
        List<Card> remainingCards = deckBuilder();
        System.out.println("Exiting from deck builder call");
        for (Card c : view.getDrawPile()) {
            if (!isUnknown(c)) {
                removeKnownCard(remainingCards, c);
            }
        }

        for (Card c : view.getDiscardPile().pile()) {
            if (!isUnknown(c)) {
                removeKnownCard(remainingCards, c);
            }
        }

        for (Card c : view.getUnresolvedCards().getUnresolvedCards()) {
            if (!isUnknown(c)) {
                removeKnownCard(remainingCards, c);
            }
        }

        Hand[] hands = new Hand[numPlayers];
        for (int playerIdx = 0; playerIdx < numPlayers; playerIdx++) {
            HandView h = view.getHandView(playerIdx);
            Hand hand = new Hand();
            for (int cardIdx = 0; cardIdx < h.size(); cardIdx++) {
                Card c = h.getCard(cardIdx);
                hand.add(normalizeCard(c));

                if (!isUnknown(c)) {
                    removeKnownCard(remainingCards, c);
                }
            }
            hands[playerIdx] = hand;
        }

        Collections.shuffle(remainingCards, getRandom());

        //gives a new hand to each player
        int nextCardIdx = 0;
        // Hand[] hands = new Hand[numPlayers];

        // //iterates through every players hand to give them cards
        // for (int playerIdx = 0; playerIdx < numPlayers; playerIdx++) {
        //     HandView h = view.getHandView(playerIdx);
        //     Hand hand = new Hand();

        //     for (int cardIdx = 0; cardIdx < h.size(); cardIdx++) {
        //         Card c = h.getCard(cardIdx);

        //         if (isUnknown(c)) {
        //             hand.add(remainingCards.get(nextCardIdx));
        //             nextCardIdx += 1;
        //         } else {
        //             hand.add(normalizeCard(c));
        //         }

        //     }

        //     hands[playerIdx] = hand;
        // }
        System.out.println("Reached point to draw cards");
        System.out.println(remainingCards.size() + ", " + view.getDrawPileSize());
        //all this does is build the draw pile based on known and unknown cards, proceeds to inflate pile to proper size
        Deck drawPile = new Deck(false);
        for (Card c : view.getDrawPile()) {
            if (isUnknown(c)) {
                //System.out.println("Unknown card");
                if (remainingCards.isEmpty() || nextCardIdx == remainingCards.size()) {
                    Card rc = null; 
                    while (rc == null || rc.value() == Value.UNKNOWN || rc.color() == Color.UNKNOWN) {
                        rc = new Card(Color.getRandomColor(getRandom()), Value.getRandomValue(getRandom()));
                    }
                    drawPile.add(rc);
                } else {
                    drawPile.add(remainingCards.get(nextCardIdx));
                    nextCardIdx += 1;
                }
            } else {
                drawPile.add(normalizeCard(c));
            }
        }

        System.out.println("Adding last bits");
        while (drawPile.size() < view.getDrawPileSize()) {
            if (remainingCards.isEmpty() || nextCardIdx == remainingCards.size()) {
                    Card rc = null; 
                    while (rc == null || rc.value() == Value.UNKNOWN || rc.color() == Color.UNKNOWN) {
                        rc = new Card(Color.getRandomColor(getRandom()), Value.getRandomValue(getRandom()));
                    }
                    drawPile.add(rc);
                } else {
                    drawPile.add(remainingCards.get(nextCardIdx));
                    nextCardIdx += 1;
                }
        }
        System.out.println("Returning from call to gamesim");
        return new Game(drawPile, hands, Observability.FULL, view, fakeAgent(view));
    }

    //this helper function just builds a list of all the cards in a standard Uno deck, its used for the determinization process
    private List<Card> deckBuilder() {
        List<Card> cards = new ArrayList<Card>();
        Color[] colors = new Color[] { Color.BLUE, Color.GREEN, Color.RED, Color.YELLOW };
        Value[] duplicatedValues = new Value[] {
            Value.ONE, Value.TWO, Value.THREE, Value.FOUR, Value.FIVE,
            Value.SIX, Value.SEVEN, Value.EIGHT, Value.NINE,
            Value.SKIP, Value.REVERSE, Value.DRAW_TWO
        };

        for (Color color : colors) {
            cards.add(new Card(color, Value.ZERO));

            for (Value value : duplicatedValues) {
                cards.add(new Card(color, value));
                cards.add(new Card(color, value));
            }
        }

        //mutable Game objects cannot contain UNKNOWN cards in the deck, basically made the autograde get to failing
        //give wilds a placeholder concrete color, the chosen color is set when played.
        for (int i = 0; i < 4; i++) {
            cards.add(new Card(Color.RED, Value.WILD));
            cards.add(new Card(Color.RED, Value.WILD_DRAW_FOUR));
        }

        return cards;
    }


    private boolean isUnknown(final Card c) {
        return c.value() == Value.UNKNOWN;
    }

    private Card normalizeCard(final Card c) {
    if (c.value().isWild() && c.color() == Color.UNKNOWN) {
        return new Card(Color.RED, c.value());
    }

    return c;
    }


    //removes a known card form the list
    private void removeKnownCard(final List<Card> cards, final Card target) {
        for (int i = 0; i < cards.size(); i++) {
            Card current = cards.get(i);

            if (target.value().isWild()) {
                if (current.value() == target.value()) {
                    cards.remove(i);
                    return;
                }
            } else if (current.equals(target)) {
                cards.remove(i);
                return;
            }
        }
    }
}