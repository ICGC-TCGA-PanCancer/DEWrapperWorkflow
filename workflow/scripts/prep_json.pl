use strict;
use JSON;

# this script will create a unified JSON from multiple sub docs

my $ret = {};

for (my $i=0; $i<scalar(@ARGV); $i+=2) {

  my $name = $ARGV[$i];
  my $path = $ARGV[$i+1];

  $ret->{$name} = read_json($path);

}

print to_json($ret);


sub read_json {
    my ($file) = @_;

    my $out = "";
    open IN, '<', $file or die "CANNOT OPEN FILE: '$file'\n";
    while (<IN>) {
      $_ =~ s/\n/ /g;
      $out .= $_;
    }

    my $obj = decode_json($out);

    return $obj;
}
