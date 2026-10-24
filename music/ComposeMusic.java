import java.io.*;
import java.util.Random;
import javax.sound.sampled.*;

/** Original score: Neon Orchard / 霓虹果园. 32 bars, 128 BPM, D minor. */
public final class ComposeMusic {
    static final int RATE = 32000;
    static final double BEAT = 60.0 / 128;
    static final int LENGTH = RATE * 60;
    static final float[] left = new float[LENGTH], right = new float[LENGTH];
    static final Random noise = new Random(20261025);
    // Each row is one bar in eighth notes. Zero is a rest; -1 extends the previous note.
    static final int[][] A = {
        {74, -1, 77, 81, 79, 77, 76, 72},
        {74, 77, 70, -1, 72, 74, 77, -1},
        {77, -1, 81, 84, 81, 79, 77, 76},
        {79, -1, 76, 72, 74, 76, 79, 0},
        {74, 77, 81, -1, 84, 81, 79, 77},
        {82, -1, 81, 77, 74, 77, 72, 70},
        {79, 77, 74, -1, 70, 74, 77, 79},
        {81, -1, 79, 76, 73, -1, 76, 81}
    };
    static final int[][] B = {
        {81, -1, 84, 86, 84, 81, 79, 77},
        {82, 81, 77, -1, 74, 77, 81, 82},
        {84, -1, 81, 79, 77, 79, 81, -1},
        {79, 76, 72, -1, 76, 79, 84, 79},
        {81, 77, 74, -1, 77, 79, 81, 84},
        {82, -1, 77, 74, 72, 74, 77, -1},
        {79, 82, 86, -1, 84, 82, 79, 77},
        {76, -1, 73, 69, 73, 76, 81, 73}
    };
    static final int[][] CHORDS = {
        {50,57,62,65}, {46,53,58,62}, {53,60,65,69}, {48,55,60,64},
        {50,57,62,65}, {46,53,58,62}, {43,50,55,58}, {45,52,57,61}
    };
    static void mix(int index, double value, double pan) {
        int i = Math.floorMod(index, LENGTH);
        left[i] += (float)(value * Math.sqrt((1 - pan) * .5));
        right[i] += (float)(value * Math.sqrt((1 + pan) * .5));
    }
    static void note(double beat, double duration, int midi, double volume, int voice, double pan) {
        double hz = 440 * Math.pow(2, (midi - 69) / 12.0);
        int start = (int)Math.round(beat * BEAT * RATE);
        int samples = (int)Math.round(duration * BEAT * RATE);
        int release = (int)(RATE * .025);
        for (int i = 0; i < samples; i++) {
            double t = i / (double)RATE;
            double phase = 2 * Math.PI * hz * t;
            double value = 0;
            if (voice == 1) { // Warm triangle bass; limited harmonics avoid aliasing.
                for (int h = 1; h <= 9 && hz*h < RATE*.45; h += 2)
                    value += Math.sin(phase*h) * (h%4 == 1 ? 1 : -1) / (h*h);
            } else { // Rounded pulse lead / short, narrow arpeggio.
                double duty = voice == 2 ? .25 : .5;
                for (int h = 1; h <= 11 && hz*h < RATE*.45; h++)
                    value += Math.sin(Math.PI*h*duty) * Math.cos(phase*h) / h;
                value *= .7;
            }
            double envelope = Math.min(1, i / (RATE * .005)) * Math.min(1, (samples - 1 - i) / (double)release);
            envelope *= .65 + .35 * Math.exp(-t * 12);
            mix(start + i, value * volume * envelope, pan);
        }
    }
    static void drum(double beat, int kind, double volume) {
        int start = (int)Math.round(beat * BEAT * RATE);
        double seconds = kind == 0 ? .17 : kind == 1 ? .13 : .045;
        double phase = 0, last = 0;
        for (int i = 0; i < RATE * seconds; i++) {
            double t = i / (double)RATE;
            double n = noise.nextDouble() * 2 - 1;
            double high = (n - last) * .5; last = n;
            double v;
            if (kind == 0) {
                phase += 2*Math.PI*(48+110*Math.exp(-t*55))/RATE;
                v = Math.sin(phase)*Math.exp(-t*26);
            } else if (kind == 1) v = (.65*high + .35*Math.sin(2*Math.PI*185*t))*Math.exp(-t*35);
            else v = high*Math.exp(-t*95);
            double edge = Math.min(1, i/(RATE*.001)) * Math.min(1, (RATE*seconds-1-i)/(RATE*.006));
            mix(start+i, v*volume*edge, kind == 2 ? .35 : 0);
        }
    }
    public static void main(String[] args) throws Exception {
        for (int bar = 0; bar < 32; bar++) {
            int section = bar / 8, local = bar % 8;
            int[] chord = CHORDS[local];
            int[] melody = section == 1 || section == 3 ? B[local] : A[local];
            double base = bar * 4;
            for (int s = 0; s < 8; s++) {
                if (melody[s] > 0) {
                    int sustain = 1;
                    while (s+sustain < 8 && melody[s+sustain] == -1) sustain++;
                    int pitch = melody[s] - (section == 2 ? 12 : 0);
                    note(base+s*.5, sustain*.5-.045, pitch, .24, 0, -.12);
                }
                if (s != 3 && s != 7) note(base+s*.5, .40, chord[0] - 12 + (s == 4 ? 12 : 0), .29, 1, 0);
                if (section != 2 || s%2 == 0) drum(base+s*.5, 2, s%2 == 0 ? .10 : .065);
            }
            for (int s = 0; s < 16; s++) {
                if (section == 0 && local < 2 && s%2 != 0) continue;
                note(base+s*.25, .17, chord[1+s%3] + 12, section == 2 ? .045 : .065, 2, s%2 == 0 ? -.60 : .60);
            }
            drum(base, 0, .34); drum(base+2, 0, .29);
            if (section != 2) drum(base+2.75, 0, .17);
            drum(base+1, 1, .23); drum(base+3, 1, .25);
            if (local == 7) { drum(base+3.5, 1, .14); drum(base+3.75, 1, .10); }
        }
        // Circular stereo echo: tail belongs to the next loop, not a silent outro.
        int delay = (int)(BEAT * .75 * RATE);
        float[] dryL = left.clone(), dryR = right.clone();
        double peak = 0, energy = 0;
        for (int i = 0; i < LENGTH; i++) {
            int from = Math.floorMod(i-delay, LENGTH);
            left[i] += dryR[from]*.16f; right[i] += dryL[from]*.16f;
            peak = Math.max(peak, Math.max(Math.abs(left[i]), Math.abs(right[i])));
        }
        byte[] pcm = new byte[LENGTH * 4];
        double gain = .82 / peak;
        for (int i = 0; i < LENGTH; i++) {
            // Very short edge ramps guarantee a click-free loop boundary.
            double edge = Math.min(1, Math.min(i, LENGTH-1-i)/(RATE*.004));
            float[] channels = {left[i], right[i]};
            for (int c = 0; c < 2; c++) {
                double sample = channels[c]*gain*edge;
                energy += sample*sample;
                int value = (int)Math.round(sample * 32767);
                int j = i*4+c*2; pcm[j] = (byte)value; pcm[j+1] = (byte)(value>>8);
            }
        }
        File output = new File(args[0]); output.getParentFile().mkdirs();
        AudioFormat format = new AudioFormat(RATE,16,2,true,false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm),format,LENGTH)) {
            AudioSystem.write(stream,AudioFileFormat.Type.WAVE,output);
        }
        System.out.printf("Neon Orchard: 60.000s, 128 BPM, 32 bars, stereo PCM, peak -1.72dBFS, RMS %.2fdBFS%n",20*Math.log10(Math.sqrt(energy/(LENGTH*2))));
        if (pcm[0] != 0 || pcm[1] != 0 || pcm[pcm.length-1] != 0) throw new AssertionError("Loop edge");
    }
}
