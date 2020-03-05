package uk.ac.bris.cs.scotlandyard.model;



import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.SECRET;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame, ScotlandYardView, Consumer<Move> {

	//Collection of rounds and map graph
	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;

	//Current round and player index values
	private Integer currentPlayerIndex = 0;
	private Integer currentRoundIndex = 0;

	//Values for mrx location and reveal logic
	private int mrXpreviousLocation = 0;
	private int mrXcurrentLocation = 0;
	private int mrXtempLocation = 0;
	private int mrXtempLocation2 = 0;

	//Collection of spectators which are observers
	private List<Spectator> spectators = new ArrayList<>();

	//Player set
	private List<ScotlandYardPlayer> playerInfo = new ArrayList<>();

	//Winning players
	private Set<Colour> winningPlayers = new HashSet<>();
	private int winner;

	public ScotlandYardModel(List<Boolean> rounds,
	 			Graph<Integer, Transport> graph,
				PlayerConfiguration mrX, PlayerConfiguration firstDetective,
				PlayerConfiguration... restOfTheDetectives) {

							//Checks for Null rounds
							this.rounds = requireNonNull(rounds);
							if (rounds.isEmpty()) {
    							throw new IllegalArgumentException("Empty rounds");
							}

							//Checks for Null graph
							this.graph = requireNonNull(graph);
							if (graph.isEmpty()) {
    							throw new IllegalArgumentException("Empty graph");
							}

							//Checks to make sure mrX has the BLACK colour
							if (mrX.colour != BLACK) {
    							throw new IllegalArgumentException("MrX should be Black");
							}

							//Puts the configurations in a list so we can itterate through using itterator pattern with ':' notation.
							ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
							for (PlayerConfiguration configuration : restOfTheDetectives)
								configurations.add(requireNonNull(configuration));
							configurations.add(0, firstDetective);
							configurations.add(0, mrX);

							//Compares for duplicate colours and locations
							Set<Integer> locationSet = new HashSet<>();
							Set<Colour> colourSet = new HashSet<>();
							//Use of interation with ':' once again
							for (PlayerConfiguration configuration : configurations) {
									if (locationSet.contains(configuration.location))
										throw new IllegalArgumentException("Duplicate location");
									locationSet.add(configuration.location);

									if (colourSet.contains(configuration.colour))
											throw new IllegalArgumentException("Duplicate colour");
									colourSet.add(configuration.colour);
							}

							//Checks if player tickets correct
							//Iteration using ':' notation
							for (PlayerConfiguration c : configurations) {
									int ticketNum = 0;
									for (Map.Entry<Ticket, Integer> entry : c.tickets.entrySet()) {
											if (c.colour.isDetective()) {
													Ticket ticket = entry.getKey();
													Integer count = entry.getValue();
													if ((ticket == SECRET || ticket == DOUBLE) && count > 0) throw new IllegalArgumentException("Detective has mrX tickets");
											}
											ticketNum++;
									}
									if (ticketNum != 5) throw new IllegalArgumentException("Missing tickets");

									//Adds the player to the list of players, playerInfo
									ScotlandYardPlayer player = new ScotlandYardPlayer(c.player, c.colour, c.location, c.tickets);
									playerInfo.add(player);
							}
					}


	//Adding an observer to the set of observers
	@Override
	public void registerSpectator(Spectator spectator) {
			//Checks that spectator to add is a valid spectator
			requireNonNull(spectator);
			if(spectators.contains(spectator)) throw new IllegalArgumentException("Spectator already registered.");

			//Adds spectator to collection of spectators (observers)
			spectators.add(spectator);
	}


	@Override
	public void unregisterSpectator(Spectator spectator) {
			//Checks that spectator to remove is a valid spectator
			requireNonNull(spectator);
			if(!spectators.contains(spectator)) throw new IllegalArgumentException("Spectator doesn't exist.");

			//Removes spectator from collection
			spectators.remove(spectator);
	}


	//Gets moves for a specific location
	private Set<Move> getMoves(ScotlandYardPlayer player, Set<Integer> playerLocations, Integer currentLocation){
		Set<Move> validMoves = new HashSet<>();

		//Call edges from the current player's position
		Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(graph.getNode(currentLocation));

		//Add possible move to valid moves if checks pass
		for (Edge<Integer, Transport> edge : edges){
				Integer location = edge.destination().value();
				Ticket ticket = Ticket.fromTransport(edge.data());

				if(!playerLocations.contains(location)){
						if(player.hasTickets(ticket)) validMoves.add(new TicketMove(player.colour(), ticket, location));
						if(player.hasTickets(SECRET)) validMoves.add(new TicketMove(player.colour(), SECRET, location));
				}
		}
		return validMoves;
	}


	private Set<Move> validMove(Colour player, ScotlandYardPlayer currentPlayer) {
		//Initialise important components for this function
		Set<Move> validMoves = new HashSet<>();

		//Set of player locations excluding mrX's
		Set<Integer> playerLocations = new HashSet<>();
		for(ScotlandYardPlayer p : playerInfo) if(p.colour() != BLACK) playerLocations.add(p.location());

		//Finds all possible single moves
		Set<Move> singleMoves = getMoves(currentPlayer, playerLocations, currentPlayer.location());
		validMoves = singleMoves;

		//Double move logic
		if(player == BLACK && currentPlayer.hasTickets(DOUBLE) && (getCurrentRound() < rounds.size() - 1)){
				//Update current player locations
				Set<Move> doubleMoves = new HashSet<>();
				playerLocations.remove(currentPlayer.location());

				//Polymorphism, referring to moves of several possible types as type Move.
				for(Move firstMove : singleMoves){
						//Removes mrX old location
						//Downcasting, multiple dispatch could be used in place
						TicketMove firstTicketMove = (TicketMove) firstMove;
						Integer firstMoveDestination = firstTicketMove.destination();
						Ticket firstTicket = firstTicketMove.ticket();

						//Finds and creates all possible double moves
						Set<Move> secondMoves = getMoves(currentPlayer, playerLocations, firstMoveDestination);
						//Polymorphism and downcasting used once again
						for(Move secondMove : secondMoves){
								TicketMove secondTicketMove = (TicketMove) secondMove;
								Ticket secondTicket = secondTicketMove.ticket();
								if(((firstTicket == secondTicket) && (currentPlayer.hasTickets(firstTicket, 2))) ||
								(firstTicket != secondTicket) && ((currentPlayer.hasTickets(firstTicket) && currentPlayer.hasTickets(secondTicket)))){
										doubleMoves.add(new DoubleMove(player, firstTicketMove, secondTicketMove));
								}
						}
				}
				//Add double moves to validMoves
				for(Move move : doubleMoves) validMoves.add(move);
		}
		//If validMoves is currently empty add PassMove as no moves are possible
		if(validMoves.isEmpty()) validMoves.add(new PassMove(player));

		return validMoves;
	}

	private void doMove(Move move, ScotlandYardPlayer currentPlayer){
		//Typecast move to ticketmove and get current player
		TicketMove chosenMove = (TicketMove) move;

		//Ammend tickets and change location
		currentPlayer.removeTicket(chosenMove.ticket());
		currentPlayer.location(chosenMove.destination());

		//Transfer tickets to mrX
		if(currentPlayer.colour() != BLACK) playerInfo.get(0).addTicket(chosenMove.ticket());
	}

	private Move mrXhide(Move move, int moveNum){
			//Downcasting
			TicketMove actualMove = (TicketMove) move;
			mrXcurrentLocation = actualMove.destination();
			if(moveNum == 2){
					//Sets values to keep mrX location hidden as well as showing spectators correct move
					mrXtempLocation2 = mrXpreviousLocation;
					mrXpreviousLocation = mrXtempLocation;
					mrXtempLocation = mrXtempLocation2;
					return new TicketMove(BLACK, actualMove.ticket(), mrXtempLocation2);
			}
			else{
					return new TicketMove(BLACK, actualMove.ticket(), mrXpreviousLocation);
			}
	}

	private Move mrXupdate(Move move, int moveNum){
			//Downcasting
			TicketMove actualMove = (TicketMove) move;
			mrXcurrentLocation = actualMove.destination();
			if(moveNum == 2){
					return new TicketMove(BLACK, actualMove.ticket(), mrXcurrentLocation);
			}
			else{
					//Works inline with mrXhide logic
					mrXtempLocation = mrXpreviousLocation;
					mrXpreviousLocation = actualMove.destination();
					return move;
			}
	}

	//Notification functions for spectators (observers)
	private void notifymrXDouble(Move move){
		for(Spectator s : spectators){
				s.onMoveMade(this, move);
		}
	}

	private void notifyMoveMade(Move move, Colour colour){
		for(Spectator s : spectators){
				s.onMoveMade(this, move);
		}
	}

	private void notifyRoundStart(Move move){
		for(Spectator s : spectators){
				s.onRoundStarted(this, currentRoundIndex);
				s.onMoveMade(this, move);
		}
	}

	private void notifyRotationComplete(){
		for(Spectator s : spectators){
				s.onRotationComplete(this);
		}
	}

	private void notifyGameOver(){
		for(Spectator s : spectators){
				s.onGameOver(this, winningPlayers);
		}
	}

	//Double Move notification logic
	private void notifyDouble(DoubleMove move){
			if(((rounds.get(currentRoundIndex)) == true) && (((rounds.get(currentRoundIndex + 1)) == true))) {
					notifymrXDouble(new DoubleMove(BLACK, (TicketMove) mrXupdate(move.firstMove(), 1), (TicketMove) mrXupdate(move.secondMove(), 2)));
			}
			else if(((rounds.get(currentRoundIndex)) == true) && (((rounds.get(currentRoundIndex + 1)) == false))) {
					notifymrXDouble(new DoubleMove(BLACK, (TicketMove) mrXupdate(move.firstMove(), 1), (TicketMove) mrXhide(move.secondMove(), 2)));
			}
			else if(((rounds.get(currentRoundIndex)) == false) && (((rounds.get(currentRoundIndex + 1)) == true))) {
					notifymrXDouble(new DoubleMove(BLACK, (TicketMove) mrXhide(move.firstMove(), 1), (TicketMove) mrXupdate(move.secondMove(), 2)));
			}
			else notifymrXDouble(new DoubleMove(BLACK, (TicketMove) mrXhide(move.firstMove(), 1), (TicketMove) mrXhide(move.secondMove(), 1)));
	}

	@Override
  public void accept(Move move) {
		//Get current players' valid moves
		ScotlandYardPlayer currentPlayer = playerInfo.get(currentPlayerIndex);
		Set<Move> validMoves = validMove(getCurrentPlayer(), currentPlayer);

		//Throw if illegal move
		requireNonNull(move);

		//Doublemove execution logic
		if(move instanceof DoubleMove){
			DoubleMove chosenDouble = (DoubleMove) move;
			currentPlayerIndex = (currentPlayerIndex + 1) % playerInfo.size();
			if (!validMoves.contains(move)) throw new IllegalArgumentException("Move not in the valid moves.");

			playerInfo.get(0).removeTicket(DOUBLE);
			notifyDouble(chosenDouble);
			currentRoundIndex += 1;

			doMove(chosenDouble.firstMove(), currentPlayer);
			if(rounds.get(currentRoundIndex - 1) == true) notifyRoundStart(mrXupdate(chosenDouble.firstMove(), 1));
			else notifyRoundStart(mrXhide(chosenDouble.firstMove(), 1));
			currentRoundIndex += 1;

			doMove(chosenDouble.secondMove(), currentPlayer);addWinners();
			if(rounds.get(currentRoundIndex - 1) == true) notifyRoundStart(mrXupdate(chosenDouble.secondMove(), 1));
			else notifyRoundStart(mrXhide(chosenDouble.secondMove(), 1));

			ScotlandYardPlayer p = playerInfo.get(currentPlayerIndex);
			if(isGameOver() == false) p.player().makeMove(this, p.location(), validMove(getCurrentPlayer(), p), this);
		}

		//Single move exeution logic
		else if(move instanceof TicketMove){
				Boolean increment = false;
				if(getCurrentPlayer() == BLACK) increment = true;
				currentPlayerIndex = (currentPlayerIndex + 1) % playerInfo.size();
				if (!validMoves.contains(move)) throw new IllegalArgumentException("Move not in the valid moves.");

				doMove(move, currentPlayer);
				if(increment == false){
						isGameOver();
						notifyMoveMade(move, currentPlayer.colour());
				}

				if(increment == true) {
						TicketMove chosenMove = (TicketMove) move;
								currentRoundIndex += 1;
								if(rounds.get(currentRoundIndex - 1) == true) {
										if(rounds.get(currentRoundIndex - 1) == true) mrXpreviousLocation = playerInfo.get(0).location();
										notifyRoundStart(move);
								}
								else {
										notifyRoundStart(new TicketMove(BLACK, chosenMove.ticket(), mrXpreviousLocation));
								}
				}

				ScotlandYardPlayer p = playerInfo.get(currentPlayerIndex);
				if(isGameOver() == false) {
						if(getCurrentPlayer() == BLACK) notifyRotationComplete();
						else if(isGameOver() == false) p.player().makeMove(this, p.location(), validMove(getCurrentPlayer(), p), this);
				}
				else notifyGameOver();
		}

		//Passmove notify
		else {
				if (!validMoves.contains(move)) throw new IllegalArgumentException("Move not in the valid moves.");
				currentPlayerIndex = (currentPlayerIndex + 1) % playerInfo.size();

				notifyMoveMade(move, currentPlayer.colour());

				ScotlandYardPlayer p = playerInfo.get(currentPlayerIndex);
				if(isGameOver() == false) {
						if(getCurrentPlayer() == BLACK) notifyRotationComplete();
						else if(isGameOver() == false) p.player().makeMove(this, p.location(), validMove(getCurrentPlayer(), p), this);
				}
				else notifyGameOver();
		}
	}


	@Override
	public void startRotate() {
		//Throw if game already over
		if((getCurrentPlayer() == BLACK) && (isGameOver() == true)) throw new IllegalStateException("Game already over.");

		//Initialises important objects for later functions
		ScotlandYardPlayer p = playerInfo.get(currentPlayerIndex);
		Set<Move> validMoves = validMove(p.colour(), p);

		//Requests move from player
		p.player().makeMove(this, p.location(), validMoves, this);
	}


	//Returns immutable collection of the spectators (observers)
	@Override
	public Collection<Spectator> getSpectators() {
			return Collections.unmodifiableList(spectators);
	}


	//Returns immutable collection of the players
	@Override
	public List<Colour> getPlayers() {
		ArrayList<Colour> colourTemp = new ArrayList<>();
		for (ScotlandYardPlayer p : playerInfo) {
				colourTemp.add(p.colour());
		}
		return Collections.unmodifiableList(colourTemp);
	}

	//Adds the winners to the winning players set. 1 is mrX wins, 2 is detectives.
	private void addWinners(){
		if(winner == 1){
				winningPlayers.add(BLACK);
		}
		else if (winner == 2){
				for(ScotlandYardPlayer p : playerInfo){
						if(p.colour() != BLACK) winningPlayers.add(p.colour());
				}
		}
	}

	//Returns immutable set collection of the winners
	@Override
	public Set<Colour> getWinningPlayers() {
		addWinners();
		return Collections.unmodifiableSet(winningPlayers);
	}


	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		//If mrX check if reveal round and deal with accordingly
		if(colour == BLACK){
				return Optional.of(mrXpreviousLocation);
		}

		//Else return detective location or optional empty
		else{
				for (ScotlandYardPlayer p : playerInfo){
						if(p.colour() == colour) return Optional.of(p.location());
				}
				return Optional.empty();
		}
	}


	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
			for(ScotlandYardPlayer p : playerInfo){
					if(p.colour() == colour) {
							//Put tickets into a set view to pull count from ticket type
							for (Map.Entry<Ticket, Integer> entry : p.tickets().entrySet()) {
									Ticket playerTicket = entry.getKey();
									Integer ticketCount = entry.getValue();
									if(ticket == playerTicket) return Optional.of(ticketCount);
							}
					}
			}
			return Optional.empty();
	}

	//Checks if player has any tickets left
	private boolean noTickets(ScotlandYardPlayer p){
			if(!(p.hasTickets(Ticket.TAXI) && p.hasTickets(Ticket.BUS) && p.hasTickets(Ticket.UNDERGROUND))) return true;
			else return false;
	}

	@Override
	public boolean isGameOver() {
		List<Integer> playerLocations = new ArrayList<>();

		//Checks if all detectives are stuck
		Set<Boolean> detectiveTickets = new HashSet<>();
		for(ScotlandYardPlayer p : playerInfo){
				if(p.colour() != BLACK){
						playerLocations.add(p.location());
						detectiveTickets.add(noTickets(p));
				}
		}
		if(!detectiveTickets.contains(false)){
				winner = 1;
				return true;
		}

		//Checks if mrX captured
		if(playerLocations.contains(playerInfo.get(0).location())){
				winner = 2;
				return true;
		}

		if(getCurrentPlayer() == BLACK){
				Set<Move> validMoves = validMove(BLACK, playerInfo.get(0));
				//Checks if mrX not captured in any round
				if(getCurrentRound() == rounds.size()){
						winner = 1;
					 	return true;
				}
				Set<Boolean> isOver = new HashSet<>();
				for(Move move : validMoves){
						if(move instanceof TicketMove){
								TicketMove possibleMove = (TicketMove) move;
								if(playerLocations.contains(possibleMove.destination())) isOver.add(true); else isOver.add(false);
						}
						//else if(move instanceof PassMove) throw new IllegalStateException();
						else if(move instanceof DoubleMove){
								DoubleMove possibleMove = (DoubleMove) move;
								if(playerLocations.contains(possibleMove.firstMove().destination())) isOver.add(true); else isOver.add(false);
								if(playerLocations.contains(possibleMove.secondMove().destination())) isOver.add(true); else isOver.add(false);
						}
				}
				if(!isOver.contains(false)){
						winner = 2;
					 	return true;
				}
		}
		return false;
	}


	@Override
	public Colour getCurrentPlayer() {
		return playerInfo.get(currentPlayerIndex).colour();
	}


	@Override
	public int getCurrentRound() {
		return currentRoundIndex;
	}


	@Override
	public List<Boolean> getRounds() {
			return Collections.unmodifiableList(rounds);
	}


	@Override
	public Graph<Integer, Transport> getGraph() {
			ImmutableGraph<Integer, Transport> g = new ImmutableGraph<>(graph);
			return g;
	}

}
