package com.ayush.cli;

import com.ayush.core.SearchEngine;

/**
 * Application entry point for the MiniSearch command-line interface.
 */
public final class Main {

    private Main() { }

    /**
     * Starts an interactive MiniSearch session.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        new SearchCli(new SearchEngine(), System.in, System.out).run();
    }
}
