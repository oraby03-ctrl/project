package com.webapp.client.presentation;

import com.webapp.shared.dto.GameStateView;

import java.util.Scanner;

public class ConsoleView {
    private final Scanner scanner = new Scanner(System.in);

    public String askUsername() {
        System.out.print("Enter username (3-24 chars): ");
        return scanner.nextLine();
    }

    public void printInfo(String message) {
        System.out.println(message);
    }

    public int[] askMove() {
        System.out.print("Enter move as row,col (0..2): ");
        String line = scanner.nextLine();
        String[] parts = line.split(",");
        if (parts.length != 2) {
            return new int[]{-1, -1};
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException ex) {
            return new int[]{-1, -1};
        }
    }

    public void printBoard(GameStateView state) {
        String[][] board = state.board();
        System.out.println();
        System.out.println("Room: " + state.roomId());
        for (int i = 0; i < 3; i++) {
            String c0 = board[i][0] == null ? " " : board[i][0];
            String c1 = board[i][1] == null ? " " : board[i][1];
            String c2 = board[i][2] == null ? " " : board[i][2];
            System.out.println(" " + c0 + " | " + c1 + " | " + c2 + " ");
            if (i < 2) {
                System.out.println("-----------");
            }
        }
        System.out.println();
    }
}
