package com.limitorderbook.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Main entry point to run all JMH benchmarks.
 * Run with: java -cp benchmarks.jar com.limitorderbook.benchmarks.BenchmarkRunner
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*OrderMatchingBenchmark.*")
                .include(".*OrderBookBenchmark.*")
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .mode(org.openjdk.jmh.annotations.Mode.AverageTime)
                .timeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .build();

        new Runner(opt).run();
    }

    /**
     * Run specific benchmark class.
     */
    public static void runMatchingBenchmark() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OrderMatchingBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }

    public static void runOrderBookBenchmark() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OrderBookBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }

    /**
     * Run quick benchmark (reduced iterations for development).
     */
    public static void runQuick() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*Benchmark.*")
                .forks(1)
                .warmupIterations(1)
                .measurementIterations(3)
                .build();
        new Runner(opt).run();
    }
}