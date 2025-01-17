import { Database } from 'sqlite';
import { Repository, Envelope, Id } from "@meshql/common";

export class SQLiteRepository implements Repository<string> {
    private db: Database;
    private collection: string;

    constructor(db: Database, collection: string) {
        this.db = db;
        this.collection = collection;
    }

    async initialize(): Promise<void> {
        await this.db.exec(`
            CREATE TABLE IF NOT EXISTS ${this.collection} (
                id TEXT PRIMARY KEY,
                payload TEXT,
                created_at INTEGER,
                deleted INTEGER DEFAULT 0
            )
        `);
    }

    create = async (envelope: Envelope<string>): Promise<Envelope<string>> => {
        const id = envelope.id ?? crypto.randomUUID();
        const created_at = envelope.created_at ? envelope.created_at.getTime() : Date.now();

        await this.db.run(
            `INSERT INTO ${this.collection} (id, payload, created_at, deleted) VALUES (?, ?, ?, ?)`,
            id,
            JSON.stringify(envelope.payload),
            created_at,
            0
        );

        return { ...envelope, id, created_at: new Date(created_at), deleted: false };
    }

    read = async (id: Id<string>): Promise<Envelope<string>> => {
        const row = await this.db.get(`SELECT * FROM ${this.collection} WHERE id = ? AND deleted = 0`, id);

        if (!row) return row;

        return {
            id: row.id,
            payload: JSON.parse(row.payload),
            created_at: new Date(row.created_at),
            deleted: !!row.deleted
        };
    }

    list = async (): Promise<Envelope<string>[]> => {
        const rows = await this.db.all(`SELECT * FROM ${this.collection} WHERE deleted = 0`);

        return rows.map(row => ({
            id: row.id,
            payload: JSON.parse(row.payload),
            created_at: new Date(row.created_at),
            deleted: !!row.deleted
        }));
    }

    remove = async (id: Id<string>): Promise<boolean> => {
        const result = await this.db.run(`UPDATE ${this.collection} SET deleted = 1 WHERE id = ?`, id);
        return result.changes! > 0;
    }

    async createMany(payloads: Envelope<string>[]): Promise<Envelope<string>[]> {
        const now = Date.now();

        let created: Envelope<string>[] = [];
        await this.db.run("BEGIN TRANSACTION");
        for (const envelope of payloads) {
            created.push(await this.create({ ...envelope, created_at: new Date(now) }));
        }
        await this.db.run("COMMIT");

        return created;
    }

    readMany = async (ids: Id<string>[]): Promise<Envelope<string>[]> => {
        const placeholders = ids.map(() => "?").join(", ");
        let query = `SELECT * FROM ${this.collection} WHERE id IN (${placeholders}) AND deleted = 0`;

        const rows = await this.db.all(
            query,
            ...ids
        );

        return rows.map(row => ({
            id: row.id,
            payload: JSON.parse(row.payload),
            created_at: new Date(row.created_at),
            deleted: !!row.deleted
        }));
    }

    removeMany = async (ids: Id<string>[]): Promise<Record<Id<string>, boolean>> => {
        const result: Record<Id<string>, boolean> = {};

        await this.db.run("BEGIN TRANSACTION");
        for (const id of ids) {
            result[id] = await this.remove(id);
        }
        await this.db.run("COMMIT");

        return result;
    }
}
