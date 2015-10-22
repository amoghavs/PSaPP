#!/usr/bin/env python

## converts a processed_trace/sysid file from a per-instruction trace to
## a per-block trace

# import standard modules
import sys
import os

# print an error message and exit
def print_error(msg):
    print 'error: ' + msg
    sys.exit(1)

## chop off the part of the hashcode that details which instruction it is for
def block_hash(h):
    return (h & 0xffffffffffff0000)

# class definition
class BasicBlock:
    ###### class member variables
    hashcode = 0L

    fpops = 0L
    memops = 0L

    l1hits = 0L
    l1misses = 0L
    l2hits = 0L
    l3hits = 0L
    
    ###### class function definitions
    # constructor
    def __init__(self, hashc):
        self.hashcode = hashc

    # another built-in function. turns this object into a string form
    def __str__(self):
        s = ''
        s += str(self.hashcode) +'\t'
        s += str(self.fpops) +'\t'
        s += str(self.memops) +'\t'
        s += '%.6f' % (self.get_l1hr()) +'\t'
        s += '%.6f' % (self.get_l2hr()) +'\t'
        s += '%.6f' % (self.get_l3hr())
        return s

    def get_l1hr(self):
        if (self.l1hits + self.l1misses == 0L):
            return 100.0
        return float(self.l1hits) / float(self.l1hits + self.l1misses) * 100.0

    def get_l2hr(self):
        if (self.l1hits + self.l1misses == 0L):
            return 100.0
        return float(self.l1hits + self.l2hits) / float(self.l1hits + self.l1misses) * 100.0

    def get_l3hr(self):
        if (self.l1hits + self.l1misses == 0L):
            return 100.0
        return float(self.l1hits + self.l2hits + self.l3hits) / float(self.l1hits + self.l1misses) * 100.0

    # parses input file line and adds to this block's stats
    def add_insn(self, toks, lineno):
        try:
            fpops = int(toks[1])
            memops = int(toks[2])
            l1hr = float(toks[3]) / 100.0
            l2hr = 0.0
            if len(toks) > 4:
                l2hr = float(toks[4]) / 100.0
            l3hr = 0.0
            if len(toks) > 5:
                l3hr = float(toks[5]) / 100.0

        except ValueError:
            print_error('non-numeric on line ' + lineno + ': ' + toks)

        l1hits = float(memops) * l1hr
        l1misses = memops - l1hits
        l2hits = float(memops) * (l2hr - l1hr)
        l3hits = float(memops) * (l3hr - l2hr)

        self.fpops += fpops
        self.memops += memops
        self.l1hits += l1hits
        self.l1misses += l1misses
        self.l2hits += l2hits
        self.l3hits += l3hits


def main():
    if len(sys.argv) != 2:
        print_error('usage: ' + sys.argv[0] + ' <per_insn_sysid_file')
    if not os.path.isfile(sys.argv[1]):
        print_error('cannot find file: ' + sys.argv[1])

    # open input file
    f = open(sys.argv[1], 'r')

    lineno = 0
    # dictionary (hashtable)
    block_data = {}

    for line in f:
        lineno += 1

        # is a comment
        if line.startswith('#'):
            print line,
            continue

        # tokenize input file line
        toks = line.strip().split()

        # get block hashcode
        h = 0
        try:
            h = block_hash(int(toks[0]))
        except ValueError:
            print_error('not a valid hashcode on line ' + lineno + ': ' + toks[0])

        # never seen this hashcode before
        if not block_data.has_key(h):
            block_data[h] = BasicBlock(h)

        # pile instruction stats onto block
        block_data[h].add_insn(toks, lineno)

    f.close()

    # print all blocks
    for h in block_data.keys():
        # calls BasicBlock.__str__
        print str(block_data[h])

if __name__ == '__main__':
    main()
