import { App } from 'aws-cdk-lib';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { Lunch2Stack } from '../lib/lunch2-stack.js';

const appDir = resolve(import.meta.dirname, '../../backend/target/quarkus-app');
if (!existsSync(resolve(appDir, 'quarkus-run.jar'))) {
  throw new Error(`Build output not found at ${appDir}. Run ./scripts/build-all.sh first.`);
}

const app = new App();
new Lunch2Stack(app, 'Lunch2', {
  appDir,
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.AWS_REGION ?? 'eu-central-1' },
});
