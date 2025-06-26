

package com.anode.workflow;

import java.util.Random;


public class RandomGen {

  // generate a random number between the two specified number both sides included
  public static int get(int from, int to) {
    Random random = new Random(System.nanoTime());
    int r = random.nextInt(to - from + 1) + from;
    return r;
  }

}
