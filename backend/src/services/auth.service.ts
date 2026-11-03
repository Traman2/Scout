import bcrypt from 'bcrypt';
import logger from '#configs/logger.js';
import { db } from '#configs/database.js';
import { users } from '#models/user.model.js';
import { eq } from 'drizzle-orm';

export const hashPassword = async (password: string) => {
  try {
    return await bcrypt.hash(password, 10);
  } catch (e) {
    logger.error('Error hashing password', e);
    throw new Error('Error hashing ', { cause: e });
  }
};

export const comparePassword = async (
  password: string,
  hashedPassword: string
) => {
  try {
    return await bcrypt.compare(password, hashedPassword);
  } catch (e) {
    logger.error('Error comparing password', e);
    throw new Error('Error comparing password', { cause: e });
  }
};

export const authenticateUser = async ({
  email,
  password,
}: {
  email: string;
  password: string;
}) => {
  try {
    const existingUser = await db
      .select()
      .from(users)
      .where(eq(users.email, email))
      .limit(1);

    if (existingUser.length === 0) throw new Error('User not found');

    const user = existingUser[0];

    const isValidPassword = await comparePassword(password, user.password);

    if (!isValidPassword) throw new Error('Invalid password');

    logger.info(`User ${user.email} authenticated sucessfully`);
    return user;
  } catch (e) {
    logger.error('Error authenticating the user', e);
    throw e;
  }
};

export const getUserById = async (id: number) => {
  try {
    const existingUser = await db
      .select({
        id: users.id,
        name: users.name,
        email: users.email,
        role: users.role,
      })
      .from(users)
      .where(eq(users.id, id))
      .limit(1);

    if (existingUser.length === 0) throw new Error('User not found');

    return existingUser[0];
  } catch (e) {
    logger.error('Error fetching user by id', e);
    throw e;
  }
};

export const createUser = async ({
  name,
  email,
  password,
  role = 'user',
}: {
  name: string;
  email: string;
  password: string;
  role?: string;
}) => {
  try {
    const existingUser = await db
      .select()
      .from(users)
      .where(eq(users.email, email))
      .limit(1);

    if (existingUser.length > 0)
      throw new Error('User with this email already exists');

    const password_hash = await hashPassword(password);

    const [newUser] = await db
      .insert(users)
      .values({ name, email, password: password_hash, role })
      .returning({
        id: users.id,
        name: users.name,
        email: users.email,
        role: users.role,
        created_at: users.created_at,
      });

    logger.info(`User ${newUser.email} created sucessfully`);
    return newUser;
  } catch (e) {
    logger.error('Error creating the user', e);
    throw e;
  }
};
