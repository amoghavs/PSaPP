#!/usr/bin/perl

use strict;

#flash3.phase.1.meta_0000.dfp

#flash3.phase.1.0002.siminst.dfp
#flash3.phase.1.0002.siminst.static

sub hex2dec {
        unpack("N", pack("H8", substr("0" x 8 . shift, -8)));
}

my @siminst_static = `ls -1 dfp/*.siminst.static`;
my @dfp_static = `ls -1 dfp/*.siminst.dfp`;

die "Error: There can be only 1 file on static and dfp\n"
    unless (@siminst_static == 1) && (@dfp_static == 1);

my $siminst_static_file = $siminst_static[0];
chomp($siminst_static_file);
my $dfp_static_file = $dfp_static[0];
chomp($dfp_static_file);

my %seq_2_uniq;
open(SIM_STAT,"<",$siminst_static_file) or die "$siminst_static_file\n";
print "DEBUG opening $siminst_static_file \n";
while(my $line=<SIM_STAT>){
    chomp($line);
    if($line =~ /^\d+/){
        my @tokens = split /\s+/, $line;
        die "Error 1" if exists $seq_2_uniq{$tokens[0]};
        $seq_2_uniq{$tokens[0]} = $tokens[1];
        print "DEBUG set seq_2_uniq{$tokens[0]} = $tokens[1] \n";
    }
}
close(SIM_STAT) or die "$siminst_static_file\n";

my %seq_2_type;
open(DFP_STAT,"<",$dfp_static_file) or die "$dfp_static_file\n";
print "DEBUG opening $dfp_static_file \n";
while(my $line=<DFP_STAT>){
    chomp($line);
    if($line =~ /^\d+/){
        my @tokens = split /\s+/, $line;
        die "Error 2" if !exists $seq_2_uniq{$tokens[0]};
        die "Error 3" if ($seq_2_uniq{$tokens[0]} ne $tokens[1]);

        $seq_2_type{$tokens[0]} = $tokens[2];
        print "DEBUG setting seq_2_type{$tokens[0]} = $tokens[2] \n";
    }
}
close(DFP_STAT) or die "$dfp_static_file\n";

my $exec_name = undef;
my $cpu_count = undef;
if($siminst_static_file =~ /.*\/(.*)\.phase\.1\.(\d+)\.siminst\.static/){
    $exec_name = $1;
    $cpu_count = $2;
    $cpu_count =~ s/^0+//g;
} else {
    die "Error in file name\n";
}
print "$siminst_static_file $dfp_static_file exec $exec_name cpu $cpu_count\n";
print "DEBUG CPU count = $cpu_count \n";
for(my $i=0;$i<$cpu_count;$i++){
    my %bbuid_ranges;
    my $file = sprintf("dfp/%s.phase.1.meta_%04d.dfp",$exec_name,$i);
    print "OPENING FILE $file\n";
    open(TRC_FILE,"<",$file) or die "$file\n";
    my $curr_block = undef;
    my $curr_bb_id = undef;
    my $curr_range = undef;
    while(my $line=<TRC_FILE>){
        chomp($line);
        if($line =~ /^block\s+(\d+).*/){
            $curr_block = $1;
            $curr_bb_id = $seq_2_uniq{$curr_block};
            die "Error 4" unless (exists $seq_2_type{$curr_block});
            die "Error 5" unless (exists $seq_2_uniq{$curr_block});
        } elsif($line =~ /^\s+range\s+(0x.*)\s+(0x.*).*/){
            $curr_range = hex2dec($2)-hex2dec($1) + 8;
            if($curr_range < 0){
                $curr_range = -1 * $curr_range;
            }
            if(!exists $bbuid_ranges{$curr_bb_id}){
                $bbuid_ranges{$curr_bb_id} = ();
            }
            push @{$bbuid_ranges{$curr_bb_id}},$curr_range;
        } else {
            print "$line\n";
        }
    }
    close(TRC_FILE) or die "$file\n";

    my $final_file = sprintf("dfp/processed_%04d.dfp",$i);
    open(FINAL_FILE,">",$final_file) or die "$final_file";
    foreach my $key ( sort keys %bbuid_ranges ){
        my @ranges = @{$bbuid_ranges{$key}};
        my $max = -1.0;
        for(my $j=0;$j<@ranges;$j++){
            if($ranges[$j] > $max){
                $max = $ranges[$j];
            }
        }
        die "Error 6\n" if ($max < 0);
        print FINAL_FILE "$key\t$max\n";
    }
    close(FINAL_FILE) or die "$final_file";
}

