package org.drools.benchmark.manners;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import org.drools.RuleBase;
import org.drools.RuleBaseFactory;
import org.drools.WorkingMemory;
import org.drools.benchmark.waltz.WaltzBenchmark;
import org.drools.compiler.PackageBuilder;
import org.drools.rule.Package;

public class MannersBenchmark {
    /** Number of guests at the dinner (default: 16). */
    private int             numGuests  = 16;

    /** Number of seats at the table (default: 16). */
    private int             numSeats   = 16;

    /** Minimum number of hobbies each guest should have (default: 2). */
    private int             minHobbies = 2;

    /** Maximun number of hobbies each guest should have (default: 3). */
    private int             maxHobbies = 3;
    
    public static void main(final String[] args) throws Exception {
        PackageBuilder builder = new PackageBuilder();
        builder.addPackageFromDrl( new InputStreamReader( MannersBenchmark.class.getResourceAsStream( "manners.drl" ) ) );
        Package pkg = builder.getPackage();
        
        //add the package to a rulebase
        final RuleBase ruleBase = RuleBaseFactory.newRuleBase();
        ruleBase.addPackage( pkg );
        
        WorkingMemory workingMemory = ruleBase.newWorkingMemory();
        
        InputStream is = MannersBenchmark.class.getResourceAsStream( "manners64.dat" );
        List list = getInputObjects( is );
        for ( Iterator it = list.iterator(); it.hasNext(); ) {
            Object object = it.next();
            workingMemory.assertObject( object );
        }

        workingMemory.assertObject( new Count( 1 ) );

        long start = System.currentTimeMillis();
        workingMemory.fireAllRules();
        System.err.println( System.currentTimeMillis() - start );                
    }
    
    /**
     * Convert the facts from the <code>InputStream</code> to a list of
     * objects.
     */
    protected static List getInputObjects(InputStream inputStream) throws IOException {
        List list = new ArrayList();

        BufferedReader br = new BufferedReader( new InputStreamReader( inputStream ) );

        String line;
        while ( (line = br.readLine()) != null ) {
            if ( line.trim().length() == 0 || line.trim().startsWith( ";" ) ) {
                continue;
            }
            StringTokenizer st = new StringTokenizer( line,
                                                      "() " );
            String type = st.nextToken();

            if ( "guest".equals( type ) ) {
                if ( !"name".equals( st.nextToken() ) ) {
                    throw new IOException( "expected 'name' in: " + line );
                }
                String name = st.nextToken();
                if ( !"sex".equals( st.nextToken() ) ) {
                    throw new IOException( "expected 'sex' in: " + line );
                }
                String sex = st.nextToken();
                if ( !"hobby".equals( st.nextToken() ) ) {
                    throw new IOException( "expected 'hobby' in: " + line );
                }
                String hobby = st.nextToken();

                Guest guest = new Guest( name,
                                         Sex.resolve( sex ),
                                         Hobby.resolve( hobby ) );

                list.add( guest );
            }

            if ( "last_seat".equals( type ) ) {
                if ( !"seat".equals( st.nextToken() ) ) {
                    throw new IOException( "expected 'seat' in: " + line );
                }
                list.add( new LastSeat( new Integer( st.nextToken() ).intValue() ) );
            }

            if ( "context".equals( type ) ) {
                if ( !"state".equals( st.nextToken() ) ) {
                    throw new IOException( "expected 'state' in: " + line );
                }
                list.add( new Context( st.nextToken() ) );
            }
        }
        inputStream.close();

        return list;
    }

    private InputStream generateData() {
        final String LINE_SEPARATOR = System.getProperty( "line.separator" );

        StringWriter writer = new StringWriter();

        int maxMale = numGuests / 2;
        int maxFemale = numGuests / 2;

        int maleCount = 0;
        int femaleCount = 0;

        // init hobbies
        List hobbyList = new ArrayList();
        for ( int i = 1; i <= maxHobbies; i++ ) {
            hobbyList.add( "h" + i );
        }

        Random rnd = new Random();
        for ( int i = 1; i <= numGuests; i++ ) {
            char sex = rnd.nextBoolean() ? 'm' : 'f';
            if ( sex == 'm' && maleCount == maxMale ) {
                sex = 'f';
            }
            if ( sex == 'f' && femaleCount == maxFemale ) {
                sex = 'm';
            }
            if ( sex == 'm' ) {
                maleCount++;
            }
            if ( sex == 'f' ) {
                femaleCount++;
            }

            List guestHobbies = new ArrayList( hobbyList );

            int numHobbies = minHobbies + rnd.nextInt( maxHobbies - minHobbies + 1 );
            for ( int j = 0; j < numHobbies; j++ ) {
                int hobbyIndex = rnd.nextInt( guestHobbies.size() );
                String hobby = (String) guestHobbies.get( hobbyIndex );
                writer.write( "(guest (name n" + i + ") (sex " + sex + ") (hobby " + hobby + "))" + LINE_SEPARATOR );
                guestHobbies.remove( hobbyIndex );
            }
        }
        writer.write( "(last_seat (seat " + numSeats + "))" + LINE_SEPARATOR );

        writer.write( LINE_SEPARATOR );
        writer.write( "(context (state start))" + LINE_SEPARATOR );

        return new ByteArrayInputStream( writer.getBuffer().toString().getBytes() );
    }
    
}
