#!/usr/bin/perl

use strict;
use Getopt::Long;
use Switch; 

my $help_string =<<HELP_STRING;
usage: $0
  --infile  <file> 
  --outfile  <file>
  --help   

sample:
  parse.pl --infile ./temp.info --outfile summary.out
            

HELP_STRING

sub printUsage {
    my ($msg) = @_;
    print "$help_string\n";
    print "$msg\n";
    exit;
}

sub filter_event {
    my $eventstr = shift @_;
    my @cands = @_;

    for(my $i=0;$i<@cands;$i++){
        if($eventstr =~ /^$cands[$i]/){
            return $cands[$i];
        }
    }
    return undef;
}


my @search_types = qw (stop mpi %comm gflop/sec gbytes wallclock);
my @exit_types  = qw (region); # this indicates that we should stop processing the file and exit
my @skip_markers = qw (total); # some search types are repeated and these markers indicate which one to skip
my $infile;
my $outfile;
my $is_help;
my $date = undef;
my $mpi = undef;
my $percent_comm = undef;
my $gflops = undef;
my $gbytes = undef;
my $wallclock = undef;
my @percent_MPI;
my $index = 0;

my $result = Getopt::Long::GetOptions (
  'infile=s'  => \$infile,
  'outfile=s'  => \$outfile,
  'help:i'    => \$is_help
);

printUsage("Error: Invalid option") unless $result;
printUsage("") if defined($is_help);
printUsage("Error: infile (--infile) and outfile (--outfile) are required")
    unless (defined($infile) && defined($outfile));
printUsage("Error: infile and outfile can not be the same\n")
    if($infile eq $outfile);

#  my $filter_string = $all_possible_types;


open(IN,"<",$infile) or die "Error: Can not open $infile\n";
open(OUT,">",$outfile) or die "Error: Can not open $outfile\n";
while(my $line=<IN>)
{
    chomp($line);
    if($line =~ /#/)
    {
	my $token_str = $line;
	# print "original token_str $token_str\t";
        my @tokens = split / /, $token_str;   
	# print "token[0] after split $tokens[0] \n";
	my $token_to_search = $tokens[1];
	# Find the current token and see if the user has requested it to be processed
        # print "token to search is $token_to_search\n";
	my $exit_type = &filter_event($token_to_search,@exit_types);
	if(defined($exit_type)) {
		# print " we are done processing \n";
		last;
	}
	my $current_type = &filter_event($token_to_search,@search_types);
        
	if(!defined($current_type)){
           # print "Filtering $line since $token_to_search is not asked for\n";
            next;
        }
	my $skip_marker = &filter_event($token_to_search, @skip_markers);
	if(defined($skip_marker)){
            # print "Skipping $line since $token_to_search is not asked for\n";

		next;
	}

	my @value_str = split /\s+/,$line;
	switch($token_to_search)
	{
		# print "searching $token_to_search\n";
		case "stop" { my $temp = $line; $temp =~ s/\s+//g; my @temp2 = split /:/, $temp; $date = substr $temp2[1], 0 , -3; }
		case "mpi" { $mpi = $value_str[3];    }
		case "%comm" { $percent_comm = $value_str[3]; }
		case "gflop/sec" {$gflops = $value_str[3]; }
		case "gbytes" {$gbytes = $value_str[3]; }
		case "wallclock" {$wallclock = $value_str[3]; }
		case /MPI/ {$percent_MPI[$index++] = $value_str[5]; }
	}
    }
	
}

my @dateout=split /\//, $date;
$date = $dateout[0].$dateout[1]."20".$dateout[2];

print OUT "$wallclock\n$date\n$mpi\n$gflops\n$gbytes\n$percent_comm\n";
my $i;
for ($i =0; $i < $index -1; $i++)
{
	print OUT "$percent_MPI[$i]\n";
}
print OUT "$percent_MPI[$i]";

close(IN) or die "Error: Can not close $infile\n";
close(OUT) or die "Error: Can not close $outfile\n";



