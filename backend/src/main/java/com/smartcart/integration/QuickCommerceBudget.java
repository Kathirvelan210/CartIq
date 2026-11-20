package com.smartcart.integration;

import com.smartcart.exception.ApiException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/** Stores only accounting and request fingerprints, never response payloads. */
final class QuickCommerceBudget {
    static final int LIMIT = 20;
    static final int FLOOR = 0;
    private final Path statePath;
    private final int initialBalance;
    private volatile boolean checkpointChecked;

    QuickCommerceBudget(Path statePath, int initialBalance) {
        this.statePath = statePath.toAbsolutePath();
        this.initialBalance = initialBalance;
    }

    record State(int balance, boolean pending, Set<String> attempts, Set<String> evidence, int attemptLimit) {
        int allowance() {
            return pending || balance <= FLOOR ? 0 : Math.min(attemptLimit - attempts.size(), balance - FLOOR);
        }
    }

    @FunctionalInterface
    interface Lookup<T> { T call(int expectedBalance); }

    <T> T spend(String fingerprint, Lookup<T> lookup) {
        try {
            Files.createDirectories(statePath.getParent());
            Path lockPath = statePath.resolveSibling(statePath.getFileName() + ".lock");
            try (var channel = FileChannel.open(lockPath, CREATE, WRITE); var lock = channel.tryLock()) {
                if (lock == null) throw busy();
                var state = read();
                if (state.allowance() == 0 || (!checkpointChecked && initialBalance != state.balance())) {
                    throw unavailable();
                }
                checkpointChecked = true;
                var attempts = new HashSet<>(state.attempts());
                if (!attempts.add(fingerprint)) {
                    throw new QuickCommerceProvider.DuplicateLookup();
                }
                int expectedBalance = state.balance() - 1;
                // A crash, timeout or unverified response leaves the persisted reservation pending.
                write(new State(expectedBalance, true, attempts, state.evidence(),state.attemptLimit()));
                T result;
                try {
                    result = lookup.call(expectedBalance);
                } catch (QuickCommerceProvider.LookupFailure failure) {
                    if (failure.confirmedNoCharge) {
                        // The provider confirmed the full pre-dispatch balance. Preserve the attempt and fingerprint.
                        write(new State(state.balance(), false, attempts, evidence(state,fingerprint,failure.providerRequestId,"provider-no-charge"),state.attemptLimit()));
                    }
                    throw failure;
                }
                String providerId=result instanceof QuickCommerceProvider.Result response?response.requestId():null;
                write(new State(expectedBalance, false, attempts, evidence(state,fingerprint,providerId,"provider-charged"),state.attemptLimit()));
                return result;
            }
        } catch (OverlappingFileLockException busy) {
            throw busy();
        } catch (IOException | IllegalArgumentException invalid) {
            throw unavailable();
        }
    }

    record Debit(String fingerprint, String providerId, int credits) {}
    record Settlement(int balance, List<Debit> receipts) {}

    /** Reserve the whole comparison under the same inter-process lock used by single lookups. */
    boolean spendComparison(List<String> fingerprints, int verifiedBalance, Lookup<Settlement> dispatch) {
        if (fingerprints.isEmpty() || fingerprints.size()>3 || Set.copyOf(fingerprints).size()!=fingerprints.size()) throw unavailable();
        Path lockPath = statePath.resolveSibling(statePath.getFileName() + ".lock");
        try (var channel = FileChannel.open(lockPath, CREATE, WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw busy();
            State prior = read();
            if (prior.balance() != verifiedBalance || prior.allowance() < fingerprints.size()) throw unavailable();
            var attempts = new HashSet<>(prior.attempts());
            for (String fingerprint : fingerprints) {
                if (!attempts.add(fingerprint)) throw new QuickCommerceProvider.DuplicateLookup();
            }
            int reserved = prior.balance() - fingerprints.size();
            write(new State(reserved, true, attempts, prior.evidence(),prior.attemptLimit()), fingerprints);
            var settlement = dispatch.call(prior.balance());
            if (settlement == null || settlement.receipts().size() != fingerprints.size()) return false;
            int debits = 0;
            var remaining = new HashSet<>(fingerprints);
            var provisional = new HashSet<>(prior.evidence());
            var settled = new HashSet<>(prior.evidence());
            var ids = new HashSet<String>();
            for (String line : settled) ids.add(line.split("\t")[2]);
            boolean complete = true;
            for (Debit receipt : settlement.receipts()) {
                if (!remaining.remove(receipt.fingerprint()) || receipt.providerId() == null
                    || !receipt.providerId().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                    || !ids.add(receipt.providerId())) { complete=false; continue; }
                var prefix = String.join("\t","evidence",receipt.fingerprint(),receipt.providerId())+"\t";
                provisional.add(prefix+"batch-awaiting-reconciliation");
                if (receipt.credits() < 0 || receipt.credits() > 1) { complete=false; continue; }
                debits += receipt.credits();
                settled.add(prefix+(receipt.credits() == 1 ? "provider-charged" : "provider-no-charge"));
            }
            if (!complete || settlement.balance() != prior.balance() - debits) {
                write(new State(reserved,true,attempts,provisional,prior.attemptLimit()), fingerprints);
                return false;
            }
            write(new State(settlement.balance(), false, attempts, settled,prior.attemptLimit()));
            checkpointChecked = true;
            return true;
        } catch (OverlappingFileLockException conflict) {
            throw busy();
        } catch (IOException invalid) {
            throw unavailable();
        }
    }

    private Set<String> evidence(State prior,String fingerprint,String providerId,String kind) {
        if(providerId==null)return prior.evidence();
        if(prior.evidence().stream().anyMatch(line->line.split("\\t")[2].equals(providerId)))throw unavailable();
        var rows=new HashSet<>(prior.evidence());
        rows.add(String.join("\t","evidence",fingerprint,providerId,kind));
        return rows;
    }

    State inspect() {
        try { return read(); } catch (IOException failure) { throw unavailable(); }
    }

    // A local operator can explicitly extend this ledger using an absolute lifetime ceiling.
    // Reading it never grants a new window on restart or changes past reservations.
    private int attemptLimit() throws IOException {
        Path policy = statePath.resolveSibling(statePath.getFileName() + ".attempt-limit");
        try {
            String value = Files.readString(policy).strip();
            if (!value.matches("[1-9][0-9]{0,5}")) throw unavailable();
            int limit = Integer.parseInt(value);
            if (limit < LIMIT) throw unavailable();
            return limit;
        } catch (java.nio.file.NoSuchFileException missing) {
            return LIMIT;
        }
    }

    private State read() throws IOException {
        try {
            int limit = attemptLimit();
            List<String> lines = Files.readAllLines(statePath);
            if (lines.size() < 3 || lines.size() > 2 * limit + 4
                || !Set.of("cartiq-qc-budget-v1", "cartiq-qc-budget-v2","cartiq-qc-batch-pending-v1").contains(lines.get(0))
                || ("cartiq-qc-batch-pending-v1".equals(lines.get(0)) && !"pending".equals(lines.get(2)))
                || !Set.of("pending", "settled").contains(lines.get(2))) throw unavailable();
            int balance = Integer.parseInt(lines.get(1));
            boolean pending = "pending".equals(lines.get(2));
            var attempts = new HashSet<String>();
            var evidence = new HashSet<String>();
            var evidencedAttempts = new HashSet<String>();
            var requestIds = new HashSet<String>();
            boolean batch = "cartiq-qc-batch-pending-v1".equals(lines.get(0));
            List<String> reserved = List.of();
            if (batch) {
                if (lines.size()<4 || !lines.get(3).matches("batch(?:\t[0-9a-f]{64}){1,3}")) throw unavailable();
                reserved=List.of(lines.get(3).substring(6).split("\t"));
                if (Set.copyOf(reserved).size()!=reserved.size()) throw unavailable();
            }
            for (String line : lines.subList(batch?4:3, lines.size())) {
                if (line.matches("[0-9a-f]{64}")) {
                    if (!attempts.add(line)) throw unavailable();
                    continue;
                }
                var fields = line.split("\\t", -1);
                if ("cartiq-qc-budget-v1".equals(lines.get(0)) || fields.length != 4 || !"evidence".equals(fields[0])
                    || !fields[1].matches("[0-9a-f]{64}") || !evidencedAttempts.add(fields[1])
                    || !fields[2].matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                    || !requestIds.add(fields[2]) || !fields[3].matches("[a-z0-9][a-z0-9-]{0,99}")) throw unavailable();
                evidence.add(line);
            }
            if (balance < FLOOR || attempts.size() > limit || !attempts.containsAll(reserved) || !attempts.containsAll(evidencedAttempts)
                || (pending && attempts.size() == evidencedAttempts.size()
                    && evidence.stream().noneMatch(line -> line.endsWith("\tbatch-awaiting-reconciliation")))) throw unavailable();
            return new State(balance, pending, attempts, evidence, limit);
        } catch (java.nio.file.NoSuchFileException absent) {
            throw unavailable();
        }
    }

    private void write(State state) throws IOException { write(state,List.of()); }

    private void write(State state,List<String> reserved) throws IOException {
        Path staged = Files.createTempFile(statePath.getParent(), "credit-", ".tmp");
        try {
            String format = !reserved.isEmpty() ? "cartiq-qc-batch-pending-v1" : state.evidence().isEmpty() ? "cartiq-qc-budget-v1" : "cartiq-qc-budget-v2";
            var lines = new ArrayList<>(List.of(format, Integer.toString(state.balance()),
                state.pending() ? "pending" : "settled"));
            if (!reserved.isEmpty()) lines.add("batch\t"+String.join("\t",reserved));
            lines.addAll(new TreeSet<>(state.attempts()));
            lines.addAll(new TreeSet<>(state.evidence()));
            var bytes = ByteBuffer.wrap((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            try (var channel = FileChannel.open(staged, WRITE)) {
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(staged, statePath, ATOMIC_MOVE, REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static ApiException busy() {
        return new ApiException(HttpStatus.CONFLICT, "A QuickCommerce lookup is already in progress.");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QuickCommerce budget unavailable, uncertain or exhausted.");
    }
}
