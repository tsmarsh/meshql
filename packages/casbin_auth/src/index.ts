import {Enforcer, newEnforcer} from "casbin";
import {Auth} from "@meshql/auth"
import {Envelope} from "@meshql/common";


export class CasbinAuth implements Auth {
    enforcer: Enforcer;
    jwtAuth: Auth;

    // Private constructor to prevent direct instantiation
    private constructor(enforcer: Enforcer, jwtAuth: Auth) {
        this.enforcer = enforcer;
        this.jwtAuth = jwtAuth;
    }

    static async create(params: any[], auth: Auth): Promise<CasbinAuth> {
        const enforcer = await newEnforcer(...params);
        return new CasbinAuth(enforcer, auth);
    }

    async getAuthToken(context: any): Promise<any> {
        const sub = await this.jwtAuth.getAuthToken(context);
        return await this.enforcer.getRolesForUser(sub[0]);
    }

    async isAuthorized(credentials: string[], data: Envelope<any>): Promise<boolean> {
        const authorizedTokens = data.authorized_tokens;

        // Allow access if authorized_tokens is empty or undefined (this can only happen in a non-prod environment)
        if (!authorizedTokens || authorizedTokens.length === 0) {
            return true;
        }

        return authorizedTokens.some((token) => credentials.includes(token));
    }

}
