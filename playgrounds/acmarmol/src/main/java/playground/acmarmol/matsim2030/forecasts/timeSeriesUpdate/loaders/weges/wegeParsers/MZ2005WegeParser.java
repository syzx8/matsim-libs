package playground.acmarmol.matsim2030.forecasts.timeSeriesUpdate.loaders.weges.wegeParsers;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.households.Households;
import org.matsim.utils.objectattributes.ObjectAttributes;

import playground.acmarmol.matsim2030.forecasts.timeSeriesUpdate.loaders.etappes.Etappe;
import playground.acmarmol.matsim2030.forecasts.timeSeriesUpdate.loaders.weges.Wege;
import playground.acmarmol.matsim2030.microcensus2010.MZConstants;

/**
 * 
 * Parses the wegeinland.dat file from MZ2005
 *
 * @author acmarmol
 * 
 */


public class MZ2005WegeParser {


	//////////////////////////////////////////////////////////////////////
	//member variables
	//////////////////////////////////////////////////////////////////////
		
		private TreeMap<String, ArrayList<Wege>> weges;

	//////////////////////////////////////////////////////////////////////
	//constructors
	//////////////////////////////////////////////////////////////////////

		public MZ2005WegeParser(TreeMap<String, ArrayList<Wege>> weges) {
			super();
			this.weges = weges;
		}	




		public void parse(String wegeFile) throws Exception{

			FileReader fr = new FileReader(wegeFile);
			BufferedReader br = new BufferedReader(fr);
			String curr_line = br.readLine(); // Skip header	
			int skipCounter =0;
							
			int counter = 0;
			while ((curr_line = br.readLine()) != null) {
				
					
				String[] entries = curr_line.split("\t", -1);
				
				//household number
				String hhnr = entries[0].trim();
							
				//person number (zielpnr)
				String zielpnr = entries[1].trim();
				String pid = hhnr.concat(zielpnr);
				
				//wege number
				int wege_nr = Integer.parseInt(entries[3].trim());
				
				
				//WP
				String person_weight = entries[2].trim();
				
				//wege mode
				String mode = entries[52].trim();
				if(mode.equals("1")){mode =  MZConstants.PLANE;}
				else if(mode.equals("2")){mode =  MZConstants.TRAIN;}
				else if(mode.equals("3")){mode =  MZConstants.POSTAUTO;}
				else if(mode.equals("4")){mode =  MZConstants.SHIP;}
				else if(mode.equals("5")){mode =  MZConstants.TRAM;}
				else if(mode.equals("6")){mode =  MZConstants.BUS;}
				else if(mode.equals("7")){mode =  MZConstants.SONSTINGER_OEV;}
				else if(mode.equals("8")){mode =  MZConstants.REISECAR;}
				else if(mode.equals("9")){mode =  MZConstants.CAR;}
				else if(mode.equals("10")){mode =  MZConstants.TRUCK;}
				else if(mode.equals("11")){mode =  MZConstants.TAXI;}
				else if(mode.equals("12")){mode =  MZConstants.MOTORCYCLE;}
				else if(mode.equals("13")){mode =  MZConstants.MOFA;}
				else if(mode.equals("14")){mode =  MZConstants.BICYCLE;}
				else if(mode.equals("15")){mode =  MZConstants.WALK;}
				else if(mode.equals("16")){mode =  MZConstants.SKATEBOARD;}
				else if(mode.equals("17")){mode =  MZConstants.OTHER;}
				else if(mode.equals("-99")){mode =  MZConstants.PSEUDOETAPPE;} else
					throw new RuntimeException("This should never happen!  Mode: " +  mode + " doesn't exist");
				
				//purpose
				String wzweck1 = entries[55].trim();
				String wzweck2 = entries[54	].trim();
				String ausnr = entries[58].trim();
				String purpose ="";
				
						
				if(wzweck2.equals("1") || ausnr.equals("-97")){
				//hinweg or last wege of incomplete ausgaenge: for some reason with incomplete ausgaenges,
				// if wzweck = "2" doesnt necesarilly implies a Nachhauseweg  (maybe explained somewhere in documentation?)
					
				if(wzweck1.equals("1")){wzweck1 = MZConstants.CHANGE ;}
				else if(wzweck1.equals("2")){purpose =  MZConstants.WORK;}
				else if(wzweck1.equals("3")){purpose =  MZConstants.EDUCATION;}
				else if(wzweck1.equals("4")){purpose =  MZConstants.SHOPPING;}
				else if(wzweck1.equals("5")){purpose =  MZConstants.ERRANDS;}
				else if(wzweck1.equals("6")){purpose =  MZConstants.BUSINESS;}
				else if(wzweck1.equals("7")){purpose =  MZConstants.DIENSTFAHRT;}
				else if(wzweck1.equals("8")){purpose =  MZConstants.LEISURE;}
				else if(wzweck1.equals("9")){purpose =  MZConstants.ACCOMPANYING_CHILDREN;}
				else if(wzweck1.equals("10")){purpose = MZConstants.ACCOMPANYING_NOT_CHILDREN;}
				else if(wzweck1.equals("11")){purpose=  MZConstants.FOREIGN_PROPERTY;}
				else if(wzweck1.equals("12")){purpose =  MZConstants.OTHER;}
				//else if(wzweck1.equals("13")){purpose = "border crossing";}
				else if(wzweck1.equals("-99")){purpose = MZConstants.PSEUDOETAPPE;} else
					throw new RuntimeException("This should never happen!  Purpose wzweck1: " +  wzweck1 + " doesn't exist");
				}else if(wzweck2.equals("2") || wzweck2.equals("3") ){// Nachhauseweg or Weg von zu Hause nach Hause
					purpose =  MZConstants.HOME;	} else
					throw new RuntimeException("This should never happen!  Purpose wzweck2: " +  wzweck2 + " doesn't exist");
				
				//distance
				boolean skip = false; //skip wege if no distance can be estimated <1%
				double distance = Double.parseDouble(entries[50].trim());
				if(distance == -99){ //S_QAL <> 1 oder Z_QAL <>1. Although precision is not the best, distance will anyway be computed as
											//the euclidian distance
					double  xs = Double.parseDouble(entries[18]); 
					double  ys = Double.parseDouble(entries[19]); 
					double  xz = Double.parseDouble(entries[30]); 
					double  yz = Double.parseDouble(entries[31]); 
					
					if(xs!=-97 && ys!=-97 && xz!=-97 && yz!=-97){	
						distance = Math.sqrt(Math.pow(xz-xs, 2) + Math.pow(yz-ys, 2))/1000; 
					}else{
						skip = true;
						counter++;
						System.out.println("skipped: " + counter);
					}
				}
				
				//duration
				String duration = entries[56];
				if(duration.equals("-99")||duration.equals(" ")){ //pseudoetappe
					skip = true;
				}
				
				
				//create new wege
				if(!skip){
					Wege wege = new Wege();
					wege.setWeight(person_weight);
					wege.setMode(mode);
					wege.setPurpose(purpose);
					wege.setDuration(duration);
					wege.setDistance(String.valueOf(distance));
					wege.setWegeNr(wege_nr);
					
					
					//add it to list
					if(!this.weges.containsKey(pid)){
						this.weges.put(pid, new ArrayList<Wege>());
					}
					this.weges.get(pid).add(wege);
				}else{
					skipCounter++;
				}
				
				
				
			}
			
			System.out.println("Skipped " + skipCounter + " weges.");
		}
		

}
			