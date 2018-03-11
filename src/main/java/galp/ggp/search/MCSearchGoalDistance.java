package galp.ggp.search;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import galp.ggp.statemachine.TimeOutException;
import galp.ggp.statemachine.TrialPropNetStateMachine;
import is.ru.cadia.ggp.propnet.structure.PropNetStructure;

public class MCSearchGoalDistance {
	TrialPropNetStateMachine orginalPropNetStateMachine;
	TrialPropNetStateMachine reducedPropNetStateMachine;
	PropNetStructure structure;
	int nStates;
	int minGoal = 50;

	public MCSearchGoalDistance(TrialPropNetStateMachine orginalPropNetStateMachine,
			TrialPropNetStateMachine reducedPropNetStateMachine) {
		this.orginalPropNetStateMachine = orginalPropNetStateMachine;
		this.reducedPropNetStateMachine = reducedPropNetStateMachine;
	}

	public Move search(Node root, long timeout, Role role)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		nStates = 0;
		try {
			mcSearch(root, timeout);
		} catch (TimeOutException e) {

			System.out.println("no more time, Get out, out, out .... ");
		}

		// finding the move with bigest N
		Move bestMove = null;
		for (int r = 0; r < orginalPropNetStateMachine.getRoles().size(); r++) {
			if (orginalPropNetStateMachine.getRoles().get(r).equals(role)) {
				int i = 0;
				int max = root.N[r][0];
				for (int m = 1; m < root.N[r].length; m++) {
					if (root.N[r][m] > max) {
						max = root.N[r][m];
						i = m;
					}
				}

				bestMove = root.legalActions.get(r).get(i);

			}
		}

		for (int r = 0; r < root.N.length; r++) {
			for (int m = 0; m < root.N[r].length; m++) {
				System.out.println(root.legalActions.get(r).get(m) + "|Q: " + root.Q[r][m] + " |N: " + root.N[r][m]);
			}

		}

		System.out.println(bestMove + " n of simulations: " + nStates);
		System.out.println();
		// returning the move.
		return bestMove;
	}

	// this method make a new tree node from game state.
	// Q and N are stored in two dimensional arrays where first index is the role
	// the belong to and second is the action. they have the same size/dimension as
	// the legalAction list of lists
	// where are all the legal moves for all the roles.
	// next is hash map, maping the string representation of joint move to a Node,
	// representing state after that move.
	public Node initNextNode(MachineState state, Node parent, List<Integer> moveFromParent)
			throws MoveDefinitionException {

		List<List<Move>> legalActions = new ArrayList<List<Move>>();
		for (Role role : orginalPropNetStateMachine.getRoles()) {
			legalActions.add(orginalPropNetStateMachine.getLegalMoves(state, role));
		}
		// System.out.println(legalActions);
		double[][] Q = new double[legalActions.size()][];
		int[][] N = new int[legalActions.size()][];
		int i = 0;
		for (List<Move> m : legalActions) {
			Q[i] = new double[m.size()];
			N[i] = new int[m.size()];
			i++;
		}
		Hashtable<String, Node> next = new Hashtable<String, Node>();

		return new Node(state, parent, legalActions, next, Q, N, moveFromParent);
	}

	// Main method of the search, going through all four stages.
	public void mcSearch(Node root, long timeout)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException, TimeOutException {
		List<Integer> indexes;
		List<Move> moves;

		while (System.currentTimeMillis() + 100 <= timeout) {
			nStates++;
			Node node = root;
			// selection selection(node) chooses the best actions for all players and if we
			// have corresponding state in tree its repeated there.
			while (true) {
				moves = new ArrayList<Move>();
				indexes = selection(node);
				// contents = "[";
				for (int i = 0; i < indexes.size(); i++) {
					Move m = node.legalActions.get(i).get(indexes.get(i));
					// contents += m.toString() + ", ";
					moves.add(m);
				}
				// contents = contents.substring(0, contents.length() - 2);
				// contents += "]";
				if (node.exploredChildren.containsKey(indexes.toString())) {
					node = node.exploredChildren.get(indexes.toString());
					if (node.terminal)
						break;
				} else {
					break;
				}
			}
			if (node.terminal) {

				backProp(node, node.values);
			} else {

				// EXPAND
				// - find all legal moves from state
				List<List<Move>> legalMoves = orginalPropNetStateMachine.getLegalJointMoves(node.state);
				// - calculate heuristics for all moves
				List<Integer> moveScore = new ArrayList<>();
				MachineState nextState;
				for (List<Move> legalMove : legalMoves) {
					nextState = orginalPropNetStateMachine.getNextState(node.state, legalMove);
					moveScore.add(evaluateState(nextState));
				}
				// - find best move based on the heuristics score
				moves = legalMoves.get(getIndexOfLargest(moveScore));

				MachineState nextstate = orginalPropNetStateMachine.getNextState(node.state, moves);
				Node next = initNextNode(nextstate, node, indexes);
				if (orginalPropNetStateMachine.isTerminal(next.state)) {
					next.terminal = true;
					List<Integer> ret = new ArrayList<Integer>();
					for (Role r : orginalPropNetStateMachine.getRoles()) {
						ret.add(orginalPropNetStateMachine.getGoal(next.state, r));
					}
					next.values = ret;

					backProp(next, next.values);
					node.exploredChildren.put(indexes.toString(), next);
				} else {
					node.exploredChildren.put(indexes.toString(), next);

					// PLAYOUT
					List<Integer> value;

					value = runSimulation(next.state, timeout);

					// BACKPROP
					backProp(next, value);

				}
			}
		}

	}

	public int getIndexOfLargest(List<Integer> array) {
		if (array == null || array.size() == 0)
			return -1; // null or empty
		int index = 0;
		int largest = array.get(0);
		for (int i = 0; i < array.size(); i++) {
			if (array.get(i) > largest) {
				index = i;
				largest = array.get(i);
			}
		}
		return index;
	}

	private Integer evaluateState(MachineState tempState)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, TimeOutException {

		// MachineState reducedState = reducedPropNetStateMachine.getMachineStateFromSentenceList(tempState.getContents());
		MachineState reducedState = reducedPropNetStateMachine
				.getMachineStateFromSentenceList(orginalPropNetStateMachine.getInitialState().getContents());
		List<Integer> depths;
		// depths = runSimulationDepth(tempStatee);
		depths = runSimulationDepth(reducedState);
		// - calculate heuristic
		int score = 100 / depths.get(0);
		System.out.println("returning score: " + score);
		return score;
	}

	// simulating the random playuot of the game, returning depth of a winning
	// terminal state
	private List<Integer> runSimulationDepth(MachineState reducedState)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, TimeOutException {
		Role onePRole = reducedPropNetStateMachine.getRoles().get(0);
		int depth = 0;
		orginalPropNetStateMachine.isTerminal(reducedState);
		reducedPropNetStateMachine.isTerminal(reducedState);
		reducedPropNetStateMachine.getGoal(reducedState, onePRole);
		while ((!reducedPropNetStateMachine.isTerminal(reducedState))
				&& (reducedPropNetStateMachine.getGoal(reducedState, onePRole) > minGoal)) {
			// if (System.currentTimeMillis() + 100 >= timeout)
			// throw new TimeOutException();
			reducedState = reducedPropNetStateMachine.getNextState(reducedState,
					reducedPropNetStateMachine.getRandomJointMove(reducedState));
			depth++;
		}
		List<Integer> depthList = new ArrayList<>();
		depthList.add(depth);
		return depthList;
	}

	// the UCT selection method.
	public List<Integer> selection(Node node) {
		int C = 50;
		Random rand = new Random();
		List<List<Double>> A = new ArrayList<List<Double>>();

		List<Integer> indexes = new ArrayList<Integer>();
		// getting the A values for all the actions for all the roles.
		for (int r = 0; r < node.N.length; r++) {
			List<Double> mo = new ArrayList<Double>();
			for (int m = 0; m < node.N[r].length; m++) {
				if (node.N[r][m] == 0) {
					mo.add((double) Integer.MAX_VALUE);
				} else {
					double h = node.Q[r][m];
					double huu = node.N[r][m];
					double huuu = node.getNodeN();
					double hu = Math.log(huuu) / huu;
					double hh = Math.sqrt(hu);

					mo.add(h + C * hh);
				}
			}
			A.add(mo);
		}

		// getting index of the biggest A. if there is multiple of them than pick random
		// among the bigest.
		for (int r = 0; r < A.size(); r++) {
			double max = 0;
			List<Integer> mo = new ArrayList<Integer>();
			for (int m = 0; m < A.get(r).size(); m++) {
				if (A.get(r).get(m) > max) {
					mo.clear();
					mo.add(m);
					max = A.get(r).get(m);
				} else {
					if (A.get(r).get(m) == max) {
						mo.add(m);
					}
				}
			}
			indexes.add(mo.get(rand.nextInt(mo.size())));
		}

		return indexes;
	}

	// back probation starting with newly expanded node and updating parent until
	// there are no parents.
	public void backProp(Node node, List<Integer> value) {
		while (node.parent != null) {
			for (int i = 0; i < node.moveFromParent.size(); i++) {
				node.parent.Q[i][node.moveFromParent.get(i)] = node.parent.Q[i][node.moveFromParent.get(i)]
						+ ((value.get(i) - node.parent.Q[i][node.moveFromParent.get(i)])
								/ (node.parent.N[i][node.moveFromParent.get(i)] + 1));
				node.parent.N[i][node.moveFromParent.get(i)] += 1;
			}
			node = node.parent;
		}
	}

	// simulating the random playuot of the game, returning values for all the
	// roles.
	public List<Integer> runSimulation(MachineState state, long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException, TimeOutException {

		while (!orginalPropNetStateMachine.isTerminal(state)) {
			if (System.currentTimeMillis() + 100 >= timeout)
				throw new TimeOutException();
			state = orginalPropNetStateMachine.getNextState(state,
					orginalPropNetStateMachine.getRandomJointMove(state));
		}

		List<Integer> ret = new ArrayList<Integer>();
		for (Role r : orginalPropNetStateMachine.getRoles()) {
			ret.add(orginalPropNetStateMachine.getGoal(state, r));
		}
		return ret;

	}

}
